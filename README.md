# Specthinker

Specthinker is a small site for writing clear software specs.

It gives you a form with the main parts of a spec, then turns what you wrote into Markdown, plain text, or HTML. It is meant for getting an idea into a useful shape before building.

## Features

- Seven spec sections
- Required and optional fields
- Markdown output
- Plain text output
- HTML output
- Copy button for the generated spec
- Dark mode by default
- Light mode toggle

## Backend

The Kotlin/Spring Boot backend that powers the spec storage, render, and AI
polish lives in this same repo. See [`BACKEND.md`](./BACKEND.md) for the
HTTP API, the seven-section spec model, LLM provider details, and run / test
instructions.

## Run locally (frontend)

Install dependencies:

```bash
npm install
```

Start the dev server:

```bash
npm run dev
```

Build the site:

```bash
npm run build
```

Preview the production build:

```bash
npm run preview
```

## GitHub Pages

This repo includes a GitHub Actions workflow for GitHub Pages.

After pushing the repo to GitHub:

1. Open the repo on GitHub.
2. Go to Settings.
3. Go to Pages.
4. Under Build and deployment, choose GitHub Actions.
5. Push to the `main` branch.

The workflow will build the site and publish it to GitHub Pages.

If the repo name is not `specthinker`, update the `base` value in `vite.config.ts` before deploying.
