from __future__ import annotations

import base64
import os
import sqlite3
import time
from pathlib import Path
from typing import Any

import urllib3
from fastapi import Body, Depends, FastAPI, File, Header, HTTPException, Query, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware

LEGACY_SECRET = "WORKSHOP_SECRET_2026"
BYTES_PER_KIBIBYTE = 1024
BYTES_PER_MEBIBYTE = BYTES_PER_KIBIBYTE * BYTES_PER_KIBIBYTE
MAX_REQUIREMENT_FILE_SIZE_MEBIBYTES = 5
MAX_REQUIREMENT_FILE_SIZE_BYTES = MAX_REQUIREMENT_FILE_SIZE_MEBIBYTES * BYTES_PER_MEBIBYTE
UPLOAD_READ_CHUNK_SIZE_KIBIBYTES = 64
UPLOAD_READ_CHUNK_SIZE_BYTES = UPLOAD_READ_CHUNK_SIZE_KIBIBYTES * BYTES_PER_KIBIBYTE
ALLOWED_REQUIREMENT_FILE_EXTENSIONS = frozenset({".txt"})


def normalize_requirement_filename(filename: str | None) -> str:
    if not filename:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid file name")

    normalized_filename = filename.replace("\\", "/").rsplit("/", 1)[-1]
    has_drive_prefix = len(normalized_filename) > 1 and normalized_filename[1] == ":" and normalized_filename[0].isalpha()
    if normalized_filename in {"", ".", ".."} or "\x00" in normalized_filename or has_drive_prefix:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid file name")

    return normalized_filename


def remove_partial_upload(target: Path) -> None:
    try:
        target.unlink(missing_ok=True)
    except OSError as error:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Unable to remove partial upload",
        ) from error


async def store_requirement_upload(file: UploadFile, target: Path) -> int:
    written_bytes = 0
    target_created = False
    try:
        with target.open("xb") as destination:
            target_created = True
            while content := await file.read(UPLOAD_READ_CHUNK_SIZE_BYTES):
                if written_bytes + len(content) > MAX_REQUIREMENT_FILE_SIZE_BYTES:
                    raise HTTPException(
                        status_code=status.HTTP_413_CONTENT_TOO_LARGE,
                        detail="File exceeds the maximum allowed size",
                    )
                destination.write(content)
                written_bytes += len(content)
    except FileExistsError as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="File already exists",
        ) from error
    except HTTPException:
        if target_created:
            remove_partial_upload(target)
        raise
    except OSError as error:
        if target_created:
            remove_partial_upload(target)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Unable to store uploaded file",
        ) from error

    return written_bytes


def legacy_identity(authorization: str = Header(default="")) -> dict[str, str]:
    try:
        token = authorization.replace("Bearer ", "")
        decoded = base64.b64decode(token).decode("utf-8")
        parts = decoded.split("|")
        if len(parts) != 4 or parts[3] != LEGACY_SECRET or int(parts[2]) < int(time.time()):
            raise ValueError("invalid token")
        return {"username": parts[0], "role": parts[1]}
    except Exception as error:
        raise HTTPException(status_code=401, detail="Invalid token: " + str(error)) from error


def create_app(db_path: str | None = None, upload_dir: str | None = None) -> FastAPI:
    selected_db = db_path or os.getenv("WORKSHOP_DB_PATH", "./data/workshop.db")
    selected_uploads = upload_dir or os.getenv("WORKSHOP_UPLOAD_DIR", "./uploads")
    Path(selected_db).parent.mkdir(parents=True, exist_ok=True)
    Path(selected_uploads).mkdir(parents=True, exist_ok=True)

    connection = sqlite3.connect(selected_db, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    connection.execute("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, owner TEXT NOT NULL, priority TEXT NOT NULL, created_at TEXT NOT NULL)")
    count = connection.execute("SELECT COUNT(*) AS total FROM tasks").fetchone()["total"]
    if count == 0:
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Refactor login','Separar responsabilidades en frontend','TODO','student','HIGH',datetime('now'))")
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Review API','Revisar seguridad de endpoints','IN_PROGRESS','student','MEDIUM',datetime('now'))")
        connection.execute("INSERT INTO tasks(title,description,status,owner,priority,created_at) VALUES ('Trainer notes','Preparar retrospectiva','TODO','trainer','LOW',datetime('now'))")
        connection.commit()

    app = FastAPI(title="Workshop Requirements API", version="0.1.0")
    app.state.connection = connection
    app.state.upload_dir = selected_uploads
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    def as_task(row: sqlite3.Row) -> dict[str, Any]:
        return {
            "id": row["id"],
            "title": row["title"],
            "description": row["description"],
            "status": row["status"],
            "owner": row["owner"],
            "priority": row["priority"],
            "createdAt": row["created_at"],
        }

    @app.get("/api/tasks")
    def list_tasks(owner: str | None = Query(default=None), identity: dict[str, str] = Depends(legacy_identity)) -> list[dict[str, Any]]:
        requested_owner = owner or identity["username"]
        rows = connection.execute(
            "SELECT id,title,description,status,owner,priority,created_at "
            "FROM tasks WHERE owner = ? ORDER BY id",
            (requested_owner,),
        ).fetchall()
        result: list[dict[str, Any]] = []
        for row in rows:
            result.append(as_task(row))
        return result

    @app.post("/api/tasks", status_code=201)
    def create_task(payload: dict[str, Any] = Body(...), identity: dict[str, str] = Depends(legacy_identity)) -> dict[str, Any]:
        title = str(payload.get("title", ""))
        description = str(payload.get("description", ""))
        status = str(payload.get("status", "TODO"))
        priority = str(payload.get("priority", "MEDIUM"))
        owner = str(payload.get("owner", identity["username"]))
        cursor = connection.execute(
            "INSERT INTO tasks(title,description,status,owner,priority,created_at) "
            "VALUES (?, ?, ?, ?, ?, datetime('now'))",
            (title, description, status, owner, priority),
        )
        connection.commit()
        row = connection.execute(
            "SELECT id,title,description,status,owner,priority,created_at "
            "FROM tasks WHERE id = ?",
            (cursor.lastrowid,),
        ).fetchone()
        return as_task(row)

    @app.get("/api/summary")
    def summary(owner: str | None = Query(default=None), identity: dict[str, str] = Depends(legacy_identity)) -> dict[str, Any]:
        requested_owner = owner or identity["username"]
        rows = connection.execute(
            "SELECT status,priority FROM tasks WHERE owner = ?",
            (requested_owner,),
        ).fetchall()
        response: dict[str, Any] = {"owner": requested_owner, "total": 0, "byStatus": {}, "highPriorityOpen": 0}
        for row in rows:
            response["total"] = response["total"] + 1
            if row["status"] in response["byStatus"]:
                response["byStatus"][row["status"]] = response["byStatus"][row["status"]] + 1
            else:
                response["byStatus"][row["status"]] = 1
            if row["priority"] == "HIGH":
                if row["status"] != "DONE":
                    if row["status"] != "ARCHIVED":
                        response["highPriorityOpen"] = response["highPriorityOpen"] + 1
        return response

    @app.post("/api/requirements", status_code=status.HTTP_201_CREATED)
    async def upload_requirement(
        file: UploadFile = File(...),
        identity: dict[str, str] = Depends(legacy_identity),
    ) -> dict[str, Any]:
        filename = normalize_requirement_filename(file.filename)
        if Path(filename).suffix.lower() not in ALLOWED_REQUIREMENT_FILE_EXTENSIONS:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="File extension is not allowed",
            )

        upload_root = Path(selected_uploads).resolve()
        target = Path(selected_uploads) / filename
        try:
            if target.resolve(strict=False).parent != upload_root:
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid file name")
        except (OSError, RuntimeError) as error:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid file name") from error

        written_bytes = await store_requirement_upload(file, target)

        return {
            "owner": identity["username"],
            "fileName": filename,
            "storedAt": str(target),
            "size": written_bytes,
        }

    @app.get("/api/backend-health")
    def backend_health(identity: dict[str, str] = Depends(legacy_identity)) -> dict[str, Any]:
        url = os.getenv("AUTH_HEALTH_URL", "http://localhost:8080/q/health")
        try:
            response = urllib3.PoolManager().request("GET", url, timeout=3.0)
            return {"requestedBy": identity["username"], "status": response.status, "body": response.data.decode("utf-8")}
        except Exception as error:
            return {"requestedBy": identity["username"], "status": 0, "body": str(error)}

    @app.on_event("shutdown")
    def close_connection() -> None:
        connection.close()

    return app


app = create_app()
