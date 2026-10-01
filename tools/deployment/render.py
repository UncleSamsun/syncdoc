"""Render a complete, immutable test release. Standard library only."""
import argparse
import copy
import json
import pathlib
import re


def render(template, repository, backend_digest, web_digest, revision):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]*/[a-z0-9][a-z0-9_.-]*', repository):
        raise ValueError('repository must be a lowercase owner/name')
    if not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('revision must be a full commit SHA')
    for digest in (backend_digest, web_digest):
        if not re.fullmatch(r'sha256:[0-9a-f]{64}', digest):
            raise ValueError('both images require immutable sha256 digests')
    result = copy.deepcopy(template)
    found = set()
    for obj in result['items']:
        if obj['metadata'].get('namespace') != 'syncdoc-test' or obj['kind'] not in {
                'Deployment', 'Service', 'ConfigMap', 'PersistentVolumeClaim'}:
            raise ValueError('only approved namespaced workload resources are allowed')
        if obj['kind'] != 'Deployment' or obj['metadata']['name'] not in ('backend', 'web'):
            continue
        name = obj['metadata']['name']
        if name in found:
            raise ValueError(f'duplicate deployment: {name}')
        pod = obj['spec']['template']
        pod.setdefault('metadata', {}).setdefault('annotations', {})['syncdoc/source-revision'] = revision
        containers = pod['spec']['containers']
        if len(containers) != 1 or containers[0]['name'] != name:
            raise ValueError(f'unexpected containers in {name}')
        digest = backend_digest if name == 'backend' else web_digest
        containers[0]['image'] = f'ghcr.io/{repository}-{name}@{digest}'
        found.add(name)
    if found != {'backend', 'web'}:
        raise ValueError('release must contain both backend and web')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--backend-digest', required=True)
    parser.add_argument('--web-digest', required=True)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[2]
    template = json.loads((root / 'deploy/kubernetes/workloads.json').read_text())
    result = render(template, args.repository, args.backend_digest, args.web_digest, args.revision)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'workloads.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    (args.output / 'kustomization.yaml').write_text(
        'apiVersion: kustomize.config.k8s.io/v1beta1\nkind: Kustomization\nresources:\n  - workloads.json\n', encoding='utf-8')


if __name__ == '__main__':
    main()
