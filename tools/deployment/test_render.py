import copy
import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('render', ROOT / 'tools/deployment/render.py')
render = importlib.util.module_from_spec(spec)
spec.loader.exec_module(render)


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.digest = 'sha256:' + 'a' * 64
        self.template = json.loads((ROOT / 'deploy/kubernetes/workloads.json').read_text())

    def test_both_images_pinned_and_revision_recorded(self):
        result = render.render(self.template, 'unclesamsun/syncdoc', self.digest,
                               'sha256:' + 'b' * 64, 'c' * 40)
        apps = {o['metadata']['name']: o for o in result['items'] if o['kind'] == 'Deployment'}
        for name in ('backend', 'web'):
            self.assertTrue(apps[name]['spec']['template']['spec']['containers'][0]['image'].startswith(
                f'ghcr.io/unclesamsun/syncdoc-{name}@sha256:'))
            self.assertEqual(apps[name]['spec']['template']['metadata']['annotations']['syncdoc/source-revision'], 'c' * 40)
        self.assertEqual(self.template['items'][0]['kind'], 'PersistentVolumeClaim')

    def test_reject_mutable_tags_invalid_repository_and_revision(self):
        for repository, digest, revision in [('unclesamsun/syncdoc', 'latest', 'c' * 40),
                                             ('evil/../syncdoc', self.digest, 'c' * 40),
                                             ('unclesamsun/syncdoc', self.digest, '../main')]:
            with self.subTest(repository=repository, digest=digest, revision=revision):
                with self.assertRaises(ValueError):
                    render.render(self.template, repository, digest, self.digest, revision)

    def test_missing_web_fails_instead_of_partial_release(self):
        broken = copy.deepcopy(self.template)
        broken['items'] = [o for o in broken['items'] if o.get('kind') != 'Deployment' or o['metadata']['name'] != 'web']
        with self.assertRaises(ValueError):
            render.render(broken, 'unclesamsun/syncdoc', self.digest, self.digest, 'c' * 40)

    def test_repeatable_and_no_secrets_written(self):
        result = render.render(self.template, 'unclesamsun/syncdoc', self.digest, self.digest, 'c' * 40)
        self.assertEqual(result, render.render(self.template, 'unclesamsun/syncdoc', self.digest, self.digest, 'c' * 40))
        self.assertFalse(any(o['kind'] == 'Secret' for o in result['items']))
        self.assertTrue(all(o['metadata'].get('namespace') == 'syncdoc-test' for o in result['items']))

    def test_reject_secret_other_namespace_and_duplicate_deployment(self):
        for change in ('secret', 'namespace', 'duplicate'):
            broken = copy.deepcopy(self.template)
            if change == 'secret':
                broken['items'].append({'kind': 'Secret', 'metadata': {'name': 'bad', 'namespace': 'syncdoc-test'}})
            elif change == 'namespace':
                broken['items'][0]['metadata']['namespace'] = 'default'
            else:
                broken['items'].append(next(o for o in copy.deepcopy(broken['items'])
                                            if o['kind'] == 'Deployment' and o['metadata']['name'] == 'web'))
            with self.subTest(change=change):
                with self.assertRaises(ValueError):
                    render.render(broken, 'unclesamsun/syncdoc', self.digest, self.digest, 'c' * 40)

    def test_secret_cannot_override_database_address_and_recovery_has_startup_grace(self):
        apps = {o['metadata']['name']: o for o in self.template['items'] if o['kind'] == 'Deployment'}
        backend = apps['backend']['spec']['template']['spec']['containers'][0]
        self.assertFalse(any('secretRef' in entry for entry in backend.get('envFrom', [])))
        self.assertNotIn('SYNCDOC_DB_URL', {entry['name'] for entry in backend.get('env', [])})
        self.assertIn('SYNCDOC_DB_PASSWORD', {entry['name'] for entry in backend.get('env', [])})
        postgres = apps['postgres']['spec']['template']['spec']['containers'][0]
        self.assertGreaterEqual(postgres['startupProbe']['failureThreshold'] * postgres['startupProbe']['periodSeconds'], 300)


if __name__ == '__main__':
    unittest.main()
