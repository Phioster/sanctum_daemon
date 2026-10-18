# Notes for Claude sessions

## Commits and pull requests

- Commit as the maintainer, never as Claude:
  `git -c user.name="Phioster" -c user.email="165709682+Phioster@users.noreply.github.com" commit ...`
- No `Co-Authored-By:`, `Claude-Session:` or "Generated with" lines in commit messages or PR
  descriptions. The `commit-hygiene` check fails a pull request that contains them.
- Never commit secrets or tokens. Credentials live outside the repository.
- Work on a branch and open a pull request; `master` only changes through merged PRs.

## Project rules

- Do not rename the `nexarr_*` identifiers or `applicationId org.phioster.nexarr`: that would
  install a second app and orphan existing data.
- Keep source files under about 900 lines; keep comments short (the why, not the what).
- Anything that changes a server goes through `destructive(...)`.
- The version is only bumped for a release the maintainer asked for.
