"""Offline JAR resource export. Member identity is never a Windows filename."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile


def export_assets(source, destination):
    source, destination = Path(source), Path(destination)
    if destination.exists():
        raise FileExistsError(destination)
    manifest = {
        "source": source.name,
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "members": [],
    }
    with zipfile.ZipFile(source) as archive:
        members = archive.infolist()
        # Validate the entire archive before writing anything. Do not normalize away '..'.
        for info in members:
            path = PurePosixPath(info.orig_filename)
            if (
                path.is_absolute()
                or ".." in path.parts
                or "\\" in info.orig_filename
                or ":" in info.orig_filename
            ):
                raise ValueError(f"Unsafe archive path: {info.filename!r}")
        destination.mkdir(parents=True)
        for index, info in enumerate(members):
            if info.is_dir() or info.filename.lower().endswith(".class"):
                continue
            data = archive.read(info)
            # Ordinal directories preserve duplicate members, case collisions, and reserved names.
            suffix = PurePosixPath(info.filename).suffix
            suffix = suffix if suffix.isascii() and suffix[1:].isalnum() else ".bin"
            relative = f"members/{index:05d}/resource{suffix.lower()}"
            target = destination / relative
            target.parent.mkdir(parents=True)
            target.write_bytes(data)
            manifest["members"].append(
                {
                    "archive_index": index,
                    "source_path": info.filename,
                    "exported_path": relative,
                    "size": len(data),
                    "sha256": hashlib.sha256(data).hexdigest(),
                }
            )
    (destination / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source")
    parser.add_argument("destination")
    args = parser.parse_args()
    result = export_assets(args.source, args.destination)
    print(f"Exported {len(result['members'])} resources; SHA-256 {result['source_sha256']}")
