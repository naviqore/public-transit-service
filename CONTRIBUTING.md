# Contributing

Thanks for considering a contribution! These are guidelines, not strict rules, so use your best judgment. By
contributing, you agree to the [License](LICENSE) and confirm you have the rights to your contribution.

## Found an Issue?

- Open an issue with details or reproduction steps. Screenshots are helpful.
- Even better: submit a pull request with a fix!

## Want a Feature?

- Submit an issue to request a new feature.
- Want to build it?
    - **Major Feature**: Open an issue to discuss your proposal first.
    - **Small Feature**: Open a pull request directly.

## Submitting a Pull Request (PR)

Before submitting a PR:

1. Create a new branch: `feature/xyz` or `bugfix/xyz`.
2. Add your changes **with tests**.
3. Run all tests and ensure they pass.
4. Commit your changes using a clear, Conventional Commit message.
5. Push your branch and open a PR to `main` in GitHub. The PR title and message should conform to Conventional Commit
   guidelines.

## Commit Message Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/) to automate releases.

### Format

```
<type>(<optional scope>): <subject>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>
```

- Use the following structure for your commit messages:
    - **type**: type of the commit (e.g., `feat!`, `fix`, `docs`).
    - **scope**: optional, describes the area affected (e.g., `raptor`, `gtfs`).
    - **subject**: a concise description of the change
    - **body**: provide context for the change and contrast with previous behavior.
    - **footer**: reference issues or declare breaking changes (e.g., `Refs #123`, `Closes #123`,
      `BREAKING CHANGE: explanation`).
- Keep lines under **100 characters**.
- Use the **imperative present tense**: "change" not "changed" nor "changes".
- No dot (.) at the end of the subject line.

### Types

Use one of the following types for your commit:

- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation only
- `style`: formatting changes (no code changes)
- `refactor`: code change without feature/fix
- `perf`: performance improvement
- `test`: adding/fixing tests
- `build`: build system or CI changes
- `chore`: other changes that don't modify `src` or `test` files

## Release

We use [release-please](https://github.com/googleapis/release-please) for automated versioning and changelogs.

### How it works

1. **Commits trigger version bumps** via Conventional Commits:
    - `feat`: minor version
    - `feat!` or any commit with `BREAKING CHANGE`: major version
    - `fix`: patch version
2. **release-please** creates a PR with version bump and changelog. After merging:
3. **Maven** builds the project and publishes artifacts to GitHub Packages.
4. **Docker** builds and pushes the image to GitHub Container Registry.
5. **release-please** creates a PR to restore the **SNAPSHOT version** for continued development.

More details: [release-please Java & Maven](https://github.com/googleapis/release-please/blob/main/docs/java.md)
