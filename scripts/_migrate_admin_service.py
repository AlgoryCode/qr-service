"""Lift dashboard admin domain from qrservice to adminservice."""
from __future__ import annotations

import os
import re
import shutil

ROOT = os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "java", "com", "ael", "algoryqrservice"
)
ROOT = os.path.normpath(ROOT)
ADMIN = os.path.join(ROOT, "adminservice")

MOVES: list[tuple[str, str]] = [
    (
        "qrservice/admin/controller",
        "adminservice/presentation/controller",
    ),
    (
        "qrservice/admin/service/AdminUserService.java",
        "adminservice/application/AdminUserService.java",
    ),
    (
        "qrservice/admin/service/AdminUserCredentialService.java",
        "adminservice/application/AdminUserCredentialService.java",
    ),
    (
        "qrservice/admin/dto/AdminUserDtos.java",
        "adminservice/dto/AdminUserDtos.java",
    ),
    (
        "qrservice/service/DashboardAuthService.java",
        "adminservice/application/DashboardAuthService.java",
    ),
    (
        "qrservice/service/DashboardSessionService.java",
        "adminservice/application/DashboardSessionService.java",
    ),
    (
        "qrservice/util/DashboardSecurityUtils.java",
        "adminservice/application/DashboardSecurityUtils.java",
    ),
    (
        "qrservice/model/DashboardUser.java",
        "adminservice/infrastructure/persistence/entity/DashboardUser.java",
    ),
    (
        "qrservice/model/DashboardUserSession.java",
        "adminservice/infrastructure/persistence/entity/DashboardUserSession.java",
    ),
    (
        "qrservice/model/enums/DashboardRole.java",
        "adminservice/infrastructure/persistence/entity/enums/DashboardRole.java",
    ),
    (
        "qrservice/repository/DashboardUserRepository.java",
        "adminservice/infrastructure/persistence/repository/DashboardUserRepository.java",
    ),
    (
        "qrservice/repository/DashboardUserSessionRepository.java",
        "adminservice/infrastructure/persistence/repository/DashboardUserSessionRepository.java",
    ),
    (
        "qrservice/model/dto/DashboardAuthDtos.java",
        "adminservice/dto/DashboardAuthDtos.java",
    ),
]

PACKAGE_REPLACEMENTS = [
    ("com.ael.algoryqrservice.qrservice.admin.controller", "com.ael.algoryqrservice.adminservice.presentation.controller"),
    ("com.ael.algoryqrservice.qrservice.admin.service", "com.ael.algoryqrservice.adminservice.application"),
    ("com.ael.algoryqrservice.qrservice.admin.dto", "com.ael.algoryqrservice.adminservice.dto"),
    ("com.ael.algoryqrservice.qrservice.service.DashboardAuthService", "com.ael.algoryqrservice.adminservice.application.DashboardAuthService"),
    ("com.ael.algoryqrservice.qrservice.service.DashboardSessionService", "com.ael.algoryqrservice.adminservice.application.DashboardSessionService"),
    ("com.ael.algoryqrservice.qrservice.util.DashboardSecurityUtils", "com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils"),
    ("com.ael.algoryqrservice.qrservice.model.DashboardUserSession", "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUserSession"),
    ("com.ael.algoryqrservice.qrservice.model.DashboardUser", "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser"),
    ("com.ael.algoryqrservice.qrservice.model.enums.DashboardRole", "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole"),
    ("com.ael.algoryqrservice.qrservice.repository.DashboardUserSessionRepository", "com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.DashboardUserSessionRepository"),
    ("com.ael.algoryqrservice.qrservice.repository.DashboardUserRepository", "com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.DashboardUserRepository"),
    ("com.ael.algoryqrservice.qrservice.model.dto.DashboardAuthDtos", "com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos"),
]

NEW_PACKAGES = {
    "adminservice/application/AdminUserService.java": "com.ael.algoryqrservice.adminservice.application",
    "adminservice/application/AdminUserCredentialService.java": "com.ael.algoryqrservice.adminservice.application",
    "adminservice/application/DashboardAuthService.java": "com.ael.algoryqrservice.adminservice.application",
    "adminservice/application/DashboardSessionService.java": "com.ael.algoryqrservice.adminservice.application",
    "adminservice/application/DashboardSecurityUtils.java": "com.ael.algoryqrservice.adminservice.application",
    "adminservice/dto/AdminUserDtos.java": "com.ael.algoryqrservice.adminservice.dto",
    "adminservice/dto/DashboardAuthDtos.java": "com.ael.algoryqrservice.adminservice.dto",
    "adminservice/infrastructure/persistence/entity/DashboardUser.java": "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity",
    "adminservice/infrastructure/persistence/entity/DashboardUserSession.java": "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity",
    "adminservice/infrastructure/persistence/entity/enums/DashboardRole.java": "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums",
    "adminservice/infrastructure/persistence/repository/DashboardUserRepository.java": "com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository",
    "adminservice/infrastructure/persistence/repository/DashboardUserSessionRepository.java": "com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository",
}


def move_tree(src_rel: str, dst_rel: str) -> None:
    src = os.path.join(ROOT, src_rel.replace("/", os.sep))
    dst = os.path.join(ROOT, dst_rel.replace("/", os.sep))
    if not os.path.exists(src):
        return
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.isdir(src):
        if os.path.exists(dst):
            shutil.rmtree(dst)
        shutil.move(src, dst)
    else:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.move(src, dst)


def set_package(path_rel: str, pkg: str) -> None:
    path = os.path.join(ROOT, path_rel.replace("/", os.sep))
    if not os.path.isfile(path):
        return
    with open(path, encoding="utf-8") as f:
        content = f.read()
    content = re.sub(r"^package\s+[\w.]+;", f"package {pkg};", content, count=1, flags=re.MULTILINE)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def patch_java_files() -> None:
    base = os.path.join(ROOT, "..", "..", "..", "..")
    base = os.path.normpath(base)
    for dirpath, _, files in os.walk(base):
        if "src" + os.sep + "test" in dirpath:
            continue
        if os.sep + "src" + os.sep + "main" + os.sep not in dirpath:
            continue
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dirpath, fn)
            with open(path, encoding="utf-8") as f:
                c = f.read()
            n = c
            for old, new in PACKAGE_REPLACEMENTS:
                n = n.replace(old, new)
            if n != c:
                with open(path, "w", encoding="utf-8", newline="\n") as f:
                    f.write(n)


def main() -> None:
    for src, dst in MOVES:
        move_tree(src, dst)

    # controllers moved as directory
    for rel, pkg in NEW_PACKAGES.items():
        set_package(rel, pkg)

    ctrl_dir = os.path.join(ADMIN, "presentation", "controller")
    if os.path.isdir(ctrl_dir):
        for fn in os.listdir(ctrl_dir):
            if fn.endswith(".java"):
                set_package(f"adminservice/presentation/controller/{fn}", "com.ael.algoryqrservice.adminservice.presentation.controller")

    # Fix DashboardUser import of DashboardRole in entity file
    du = os.path.join(ADMIN, "infrastructure", "persistence", "entity", "DashboardUser.java")
    if os.path.isfile(du):
        with open(du, encoding="utf-8") as f:
            c = f.read()
        c = c.replace(
            "import com.ael.algoryqrservice.qrservice.model.enums.DashboardRole;",
            "import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;",
        )
        with open(du, "w", encoding="utf-8", newline="\n") as f:
            f.write(c)

    patch_java_files()

    # cleanup empty admin folders
    for remnant in [
        os.path.join(ROOT, "qrservice", "admin"),
    ]:
        if os.path.isdir(remnant) and not os.listdir(remnant):
            os.rmdir(remnant)

    print("adminservice migration done")


if __name__ == "__main__":
    main()
