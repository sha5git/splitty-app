# Splitty releases

This folder stores **release titles and notes** for every published version. Use it when creating GitHub Releases and to know what changed between versions.

## Layout

```
releases/
  README.md       ← this file (process + template)
  v1.0.0.md       ← notes for v1.0.0
  v1.0.1.md       ← add one file per release
  ...
```

## Creating a new release

### 1. See what changed since the last tag

```bash
git fetch --tags
git log $(git describe --tags --abbrev=0)..HEAD --oneline
```

For the **first** release after `v1.0.0`, compare from `v1.0.0` to `main`.

### 2. Copy the template

Duplicate the structure from the latest `vX.Y.Z.md` file (see **Template** below) into a new file, e.g. `v1.0.1.md`.

### 3. Fill in

| Field | Guidance |
|-------|----------|
| **Tag** | Semver: `vMAJOR.MINOR.PATCH` |
| **Range** | `previous-tag..this-tag` (commits included) |
| **Title** | `Splitty vX.Y.Z — Short theme` (used as GitHub Release title) |
| **Notes** | User-facing bullets: features, fixes, infra |

### 4. Create the release on GitHub (you control title + notes)

1. Push your code to `main` (including `releases/vX.Y.Z.md` if you track notes in-repo).
2. On GitHub: **Releases → Draft a new release**
3. **Choose a tag:** create new tag `vX.Y.Z` targeting `main`
4. **Release title** and **description:** paste from `releases/vX.Y.Z.md`
5. Click **Publish release**

**What happens next (automatic):** The **Release** workflow runs when you publish. It builds `splitty-deploy.tar.gz` and uploads it to your release. It does **not** create the release or overwrite your title/description.

**Re-build the bundle only** (release already exists): Actions → **Release** → Run workflow → enter the tag (e.g. `v1.0.0`).

### 5. Deploy on Termux

```bash
~/splitty/deploy.sh vX.Y.Z
~/splitty/start-all.sh
```

## Version bumps

| Type | When | Example |
|------|------|---------|
| **Patch** | Bug fixes, small tweaks | v1.0.0 → v1.0.1 |
| **Minor** | New features, backward compatible | v1.0.1 → v1.1.0 |
| **Major** | Breaking API or behaviour | v1.1.0 → v2.0.0 |

## Template (copy for next release)

```markdown
# vX.Y.Z

| Field | Value |
|-------|-------|
| **Tag** | `vX.Y.Z` |
| **Range** | `vPREVIOUS..vX.Y.Z` |
| **Date** | YYYY-MM-DD |
| **Deploy** | `~/splitty/deploy.sh vX.Y.Z` |

## Release title

Splitty vX.Y.Z — One-line theme

## Release notes

### Features
- ...

### Fixes
- ...

### Infrastructure / DevOps
- ...
```

## Release history

| Version | Title | File |
|---------|-------|------|
| v1.2.0 | Splitty v1.2.0 — Settlements on the expenses timeline | [v1.2.0.md](./v1.2.0.md) |
| v1.1.0 | Splitty v1.1.0 — Expense detail, edit, and mobile-friendly lists | [v1.1.0.md](./v1.1.0.md) |
| v1.0.0 | Splitty v1.0.0 — Initial Public Release | [v1.0.0.md](./v1.0.0.md) |
