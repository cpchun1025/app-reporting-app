# Project instructions

- Use TypeScript strict mode.
- Do not use `any` unless absolutely necessary.
- Follow the existing project architecture.
- Prefer small, focused changes.
- Do not modify generated files.
- Do not add dependencies unless required.
- Validate all external input.
- Never expose secrets or environment variables.

## Before changing code

- Read the related implementation and tests.
- Explain the root cause before fixing a bug.
- Check whether an existing utility can be reused.

## After changing code

- Run lint.
- Run type checking.
- Run the relevant tests.
- For UI changes, verify the flow with Playwright.
- Report which files were changed and which commands were run.