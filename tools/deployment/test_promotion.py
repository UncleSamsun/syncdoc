"""Exercise the actual workflow shell block against an isolated local Git remote."""
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASH = shutil.which('bash') if os.name != 'nt' else 'C:/Program Files/Git/bin/bash.exe'


class PromotionTest(unittest.TestCase):
    def test_first_repeat_failed_and_superseded_release(self):
        with tempfile.TemporaryDirectory(prefix='syncdoc-promotion-') as tmp:
            tmp = pathlib.Path(tmp)
            origin, repo = tmp / 'origin.git', tmp / 'repo'
            repo.mkdir()

            def git(*args, cwd=repo):
                return subprocess.check_output(['git', *args], cwd=cwd, text=True, encoding='utf-8').strip()

            git('init', '--bare', str(origin))
            git('init', '--initial-branch=main')
            git('config', 'user.name', 'test')
            git('config', 'user.email', 'test@example.invalid')
            for relative in ('tools/deployment/render.py', 'deploy/kubernetes/workloads.json'):
                target = repo / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / relative, target)
            git('add', '.')
            git('commit', '-m', 'test source')
            source = git('rev-parse', 'HEAD')
            git('remote', 'add', 'origin', str(origin))
            git('push', 'origin', 'main')
            workflow = (ROOT / '.github/workflows/publish.yml').read_text(encoding='utf-8')
            marker = '        run: |\n'
            script = '\n'.join(line[10:] for line in workflow.split(marker, 1)[1].splitlines()) + '\n'
            runner = tmp / 'runner'
            runner.mkdir()
            env = dict(os.environ, RUNNER_TEMP=str(runner), GITHUB_SHA=source,
                       GITHUB_REPOSITORY='UncleSamsun/syncdoc', GITHUB_STEP_SUMMARY=str(tmp / 'summary'),
                       BACKEND_DIGEST='sha256:' + 'a' * 64, WEB_DIGEST='sha256:' + 'b' * 64)
            env.pop('GIT_INDEX_FILE', None)

            def promote():
                return subprocess.run([BASH, '-c', script], cwd=repo, env=env,
                                      capture_output=True, text=True, encoding='utf-8')

            first = promote()
            self.assertEqual(first.returncode, 0, first.stderr)
            release = git('rev-parse', 'refs/heads/syncdoc-test-deploy', cwd=origin)
            self.assertEqual(git('ls-tree', '-r', '--name-only', release, cwd=origin).splitlines(),
                             ['kubernetes/kustomization.yaml', 'kubernetes/workloads.json'])
            repeat = promote()
            self.assertEqual(repeat.returncode, 0, repeat.stderr)
            self.assertEqual(release, git('rev-parse', 'refs/heads/syncdoc-test-deploy', cwd=origin))
            env['WEB_DIGEST'] = 'latest'
            self.assertNotEqual(promote().returncode, 0)
            self.assertEqual(release, git('rev-parse', 'refs/heads/syncdoc-test-deploy', cwd=origin))
            env['WEB_DIGEST'] = 'sha256:' + 'c' * 64
            git('commit', '--allow-empty', '-m', 'newer main')
            git('push', 'origin', 'main')
            stale = promote()
            self.assertEqual(stale.returncode, 0, stale.stderr)
            self.assertEqual(release, git('rev-parse', 'refs/heads/syncdoc-test-deploy', cwd=origin))
            env['GITHUB_SHA'] = git('rev-parse', 'HEAD')
            update = promote()
            self.assertEqual(update.returncode, 0, update.stderr)
            updated = git('rev-parse', 'refs/heads/syncdoc-test-deploy', cwd=origin)
            self.assertNotEqual(release, updated)
            self.assertEqual(git('rev-parse', updated + '^', cwd=origin), release)


if __name__ == '__main__':
    unittest.main()
