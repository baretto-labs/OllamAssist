# Documentation Build Setup Notes

## Overview

The OllamAssist documentation uses Hugo with the Docsy theme and is built automatically via GitHub Actions for deployment to GitHub Pages.

## Dependency Resolution

The documentation requires several dependencies that are now properly configured:

### Dependencies

1. **Hugo** (v0.154.5+ extended version)
   - Extended version required for SCSS compilation
   - Installed via Homebrew on macOS

2. **Node.js & npm** (v18+)
   - Required for Docsy theme dependencies
   - Used to install Bootstrap 5.3.8 and Font Awesome 6.7.0

3. **Docsy Theme** (git submodule)
   - Path: `themes/docsy`
   - Includes PostCSS configuration for asset processing

4. **Build Tools**
   - Bootstrap 5.3.8 - CSS framework
   - Font Awesome 6.7.0 - Icon library
   - PostCSS & Autoprefixer - CSS processing pipeline

## Build Configuration

### Hugo Configuration Structure

```
config/_default/hugo.yaml    # Main Hugo configuration (overrides theme config)
themes/docsy/hugo.yaml        # Docsy theme configuration (modified for local builds)
package.json                  # Node.js dependencies
```

### Key Configuration Changes

**`themes/docsy/hugo.yaml`:**
- Disabled module imports (set `disable: true` for bootstrap and Font-Awesome)
- Updated mounts to point to `node_modules/` paths
- Maintains Docsy's original structure

**`config/_default/hugo.yaml`:**
- Base configuration for the documentation site
- Overrides theme defaults when needed
- Sets site title, menu structure, and parameters

## Quick Start

### First-time Setup

```bash
cd docs
npm install
hugo server -D
```

Visit `http://localhost:1313` in your browser.

### Building for Deployment

```bash
cd docs
npm install  # Ensure dependencies are current
hugo --minify -b "https://baretto-labs.github.io/OllamAssist/"
```

Output is in `docs/public/`

## Deployment

The documentation is automatically deployed to GitHub Pages via `.github/workflows/build-docs.yml`:

1. **Trigger**: Push to `main` branch with changes in `docs/` directory
2. **Build**:
   - Checks out repository with submodules
   - Sets up Hugo and Node.js
   - Installs npm dependencies
   - Builds documentation with `hugo --minify`
3. **Deploy**: Pushes built site to `gh-pages` branch
4. **URL**: Available at `https://baretto-labs.github.io/OllamAssist/`

## Troubleshooting

### Hugo Build Fails

**Problem**: "Module not found" or SCSS compilation errors

**Solution**:
```bash
cd docs
npm install
rm -rf resources/
hugo --minify
```

### PostCSS Errors

**Problem**: "Cannot find module 'autoprefixer'"

**Solution**:
```bash
cd docs
npm install
```

### Port Already in Use

**Problem**: "Address already in use" when running Hugo server

**Solution**:
```bash
hugo server -p 1314 -D  # Use different port
```

## File Structure

```
docs/
├── config/_default/        # Hugo configuration overrides
│   └── hugo.yaml
├── content/                 # Markdown content
│   └── en/
│       ├── _index.md       # Homepage
│       └── docs/
│           ├── getting-started/
│           ├── features/
│           ├── settings/
│           └── faq/
├── static/                  # Static assets
│   ├── images/             # Screenshots (01-25)
│   └── videos/             # Tutorials
├── themes/
│   ├── docsy/              # Docsy theme (git submodule)
│   ├── bootstrap/          # Bootstrap clone (node_modules symlink)
│   └── Font-Awesome/       # Font Awesome clone (node_modules symlink)
├── node_modules/           # npm packages
├── public/                  # Built site (generated)
├── package.json            # npm dependencies
├── README.md               # Documentation guide
└── hugo.yaml.bak           # Backup of original config
```

## Git Ignore

The `.gitignore` file excludes:
- `public/` - Generated build output
- `resources/` - Hugo cache
- `node_modules/` - npm packages
- `.bak` files - Configuration backups

These are never committed to git and are generated locally or in CI/CD.

## Notes

- The `package-lock.json` file is committed to ensure reproducible builds in CI/CD
- Theme dependencies (Bootstrap, Font-Awesome) are installed via npm, not as Hugo modules
- PostCSS is configured via `themes/docsy/postcss.config.js` for CSS prefixing
- The GitHub Actions workflow automatically handles npm installation and Hugo build
