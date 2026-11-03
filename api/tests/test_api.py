import base64
import time
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import create_app


def token(username: str = "student", role: str = "STUDENT") -> str:
    raw = f"{username}|{role}|{int(time.time()) + 3600}|WORKSHOP_SECRET_2026"
    return base64.b64encode(raw.encode()).decode()


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_lists_tasks_for_owner_containing_quote(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get(
            "/api/tasks",
            params={"owner": "stu'dent"},
            headers=headers(),
        )

    assert response.status_code == 200
    assert response.json() == []


def test_summarizes_owner_containing_quote(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get(
            "/api/summary",
            params={"owner": "stu'dent"},
            headers=headers(),
        )

    assert response.status_code == 200
    assert response.json() == {
        "owner": "stu'dent",
        "total": 0,
        "byStatus": {},
        "highPriorityOpen": 0,
    }


def test_creates_task_with_quotes_in_text_fields(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={
                "title": "Review owner's requirements",
                "description": "The user's request is accepted",
                "status": "TODO",
                "priority": "MEDIUM",
            },
        )

    assert response.status_code == 201
    assert response.json()["title"] == "Review owner's requirements"
    assert response.json()["description"] == "The user's request is accepted"


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    content = b"Login must be accessible"
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", content, "text/plain")},
        )
    assert response.status_code == 201
    assert response.json() == {
        "owner": "student",
        "fileName": "requirements.txt",
        "storedAt": str(upload_dir / "requirements.txt"),
        "size": len(content),
    }
    assert (upload_dir / "requirements.txt").read_bytes() == content


def test_normalizes_upload_filename_and_keeps_destination_inside_upload_dir(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    uploads = [
        ("../posix.txt", "posix.txt"),
        (r"..\windows.txt", "windows.txt"),
        (r"C:\folder\drive.txt", "drive.txt"),
        ("../uppercase.TXT", "uppercase.TXT"),
    ]

    with TestClient(app) as client:
        for supplied_name, normalized_name in uploads:
            response = client.post(
                "/api/requirements",
                headers=headers(),
                files={"file": (supplied_name, b"content", "text/plain")},
            )
            assert response.status_code == 201
            assert response.json()["fileName"] == normalized_name
            assert response.json()["storedAt"] == str(upload_dir / normalized_name)
            assert (upload_dir / normalized_name).read_bytes() == b"content"

    assert not (tmp_path / "posix.txt").exists()
    assert not (tmp_path / "windows.txt").exists()
    assert not (tmp_path / "drive.txt").exists()


def test_rejects_upload_with_disallowed_extension(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.pdf", b"content", "application/pdf")},
        )

    assert response.status_code == 400
    assert response.json() == {"detail": "File extension is not allowed"}
    assert list(upload_dir.iterdir()) == []


def test_rejects_upload_with_invalid_filename(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        for invalid_filename in ("..", "C:relative.txt"):
            response = client.post(
                "/api/requirements",
                headers=headers(),
                files={"file": (invalid_filename, b"content", "text/plain")},
            )
            assert response.status_code == 400
            assert response.json() == {"detail": "Invalid file name"}
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("", b"content", "text/plain")},
        )

    assert response.status_code == 422
    assert list(upload_dir.iterdir()) == []


def test_rejects_upload_larger_than_limit_without_partial_file(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    content = b"x" * (5 * 1024 * 1024 + 1)

    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", content, "text/plain")},
        )

    assert response.status_code == 413
    assert response.json() == {"detail": "File exceeds the maximum allowed size"}
    assert list(upload_dir.iterdir()) == []


def test_accepts_upload_at_size_limit(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    content = b"x" * (5 * 1024 * 1024)

    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", content, "text/plain")},
        )

    assert response.status_code == 201
    assert response.json()["size"] == len(content)
    assert (upload_dir / "requirements.txt").read_bytes() == content


def test_rejects_duplicate_upload_without_overwriting_existing_file(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    upload_dir.mkdir()
    existing_content = b"existing"
    (upload_dir / "requirements.txt").write_bytes(existing_content)
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))

    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"replacement", "text/plain")},
        )

    assert response.status_code == 409
    assert response.json() == {"detail": "File already exists"}
    assert (upload_dir / "requirements.txt").read_bytes() == existing_content


def test_rejects_upload_without_valid_token(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            files={"file": ("requirements.txt", b"content", "text/plain")},
        )

    assert response.status_code == 401
    assert list(upload_dir.iterdir()) == []


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": "Bearer invalid"})
    assert response.status_code == 401
