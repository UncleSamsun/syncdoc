"""A stale Ready status must never be reported as a newly requested deployment."""
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASH = shutil.which('bash') if os.name != 'nt' else 'C:/Program Files/Git/bin/bash.exe'


class CheckTest(unittest.TestCase):
    def test_old_ready_release_fails_before_rollout_or_http_checks(self):
        with tempfile.TemporaryDirectory(prefix='syncdoc-check-') as tmp:
            tmp = pathlib.Path(tmp)
            mock = tmp / 'kubectl'
            mock.write_text('''#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CHECK_CALL_LOG"
case "$*" in
  *spec.suspend*) printf false ;;
  *artifact.revision*|*lastAppliedRevision*) printf 'syncdoc-test-deploy@sha1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' ;;
  *) printf unexpected > "$CHECK_UNEXPECTED"; exit 1 ;;
esac
''', encoding='utf-8')
            mock.chmod(0o755)
            marker = tmp / 'unexpected'
            env = dict(os.environ, CHECK_MOCK_BIN=str(tmp), CHECK_UNEXPECTED=str(marker),
                       CHECK_CALL_LOG=str(tmp / 'calls'),
                       SYNCDOC_CHECK_ATTEMPTS='1', SYNCDOC_CHECK_INTERVAL='0')
            # Let bash convert its inherited Windows PATH before prepending the fixture.
            script = 'export PATH="$CHECK_MOCK_BIN:$PATH"; bash "$1" "$2"'
            result = subprocess.run([BASH, '-c', script, 'check-test',
                                     str(ROOT / 'deploy/flux/check.sh'), 'b' * 40], env=env,
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(marker.exists(), result.stderr)
            calls = (tmp / 'calls').read_text()
            self.assertIn('artifact.revision', calls)
            self.assertIn('lastAppliedRevision', calls)


if __name__ == '__main__':
    unittest.main()
