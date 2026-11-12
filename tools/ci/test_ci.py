"""Failure-path checks for candidate and SDK verification, with no network access."""
import hashlib
import io
import tarfile
import tempfile
import unittest
from pathlib import Path

from consumer import verify
from prepare_sdk import download, extract_tools


class CandidateChecks(unittest.TestCase):
    def test_official_dot_root_tools_layout(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = root / 'tools.tar'
            with tarfile.open(archive, 'w') as output:
                for name in ['./llvm/bin/clang', './rust/lib/marker', './version.md']:
                    member = tarfile.TarInfo(name)
                    member.size = 1
                    output.addfile(member, io.BytesIO(b'x'))
            extract_tools(archive, root / 'extracted')
            self.assertTrue((root / 'extracted/llvm/bin/clang').is_file())
            self.assertTrue((root / 'extracted/rust/lib/marker').is_file())

    def test_sdk_digest_matches_downloaded_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'source'
            source.write_bytes(b'known SDK bytes')
            target = root / 'accepted'
            download(source.as_uri(), target, hashlib.sha256(source.read_bytes()).hexdigest())
            self.assertEqual(target.read_bytes(), source.read_bytes())

    def test_wrong_sdk_digest_removes_untrusted_archive(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'source'
            source.write_bytes(b'altered SDK bytes')
            target = root / 'rejected'
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                download(source.as_uri(), target, '0' * 64)
            self.assertFalse(target.exists())

    def test_missing_license_fails_before_candidate_execution(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = root / 'candidate.tar'
            with tarfile.open(archive, 'w') as output:
                member = tarfile.TarInfo('koral-0.1.0-dev/README.md')
                member.size = 1
                output.addfile(member, io.BytesIO(b'x'))
            with self.assertRaisesRegex(ValueError, 'missing LICENSE'):
                verify(archive, root / 'absent-sdk', 'smoke')

    def test_missing_dependency_notice_fails_before_candidate_execution(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = root / 'candidate.tar'
            with tarfile.open(archive, 'w') as output:
                for path in ['LICENSE', 'THIRD_PARTY_NOTICES.md', 'bin/koral', 'testing/package-lock.json']:
                    member = tarfile.TarInfo('koral-0.1.0-dev/' + path)
                    member.size = 1
                    output.addfile(member, io.BytesIO(b'x'))
            with self.assertRaisesRegex(ValueError, 'missing licenses/kotlin/LICENSE.txt'):
                verify(archive, root / 'absent-sdk', 'smoke')


if __name__ == '__main__':
    unittest.main()
