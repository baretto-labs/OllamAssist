# Documentation Setup Guide

This guide explains the Hugo documentation structure for OllamAssist and how to manage it.

## Overview

The documentation is built with **Hugo** + **Docsy theme** and automatically deployed to **GitHub Pages** via GitHub Actions.

- **Documentation Location:** `docs/` directory
- **Theme:** [Docsy](https://www.docsy.dev/) (Google's documentation theme)
- **Deployment:** GitHub Pages (`gh-pages` branch)
- **Auto-Build:** GitHub Actions (`.github/workflows/build-docs.yml`)

## Quick Start

### Prerequisites

1. **Hugo** (extended version, v0.100+)
   ```bash
   # macOS
   brew install hugo

   # Windows (Chocolatey)
   choco install hugo-extended

   # Linux (apt)
   sudo apt-get install hugo
   ```

   Verify installation:
   ```bash
   hugo version  # Should be v0.100+
   ```

2. **Node.js and npm** (v18+)
   ```bash
   # macOS
   brew install node

   # Windows
   choco install nodejs

   # Linux
   sudo apt-get install nodejs npm
   ```

   Verify installation:
   ```bash
   node --version
   npm --version
   ```

3. **Git** (for submodules)
   ```bash
   git --version
   ```

### Initial Setup

```bash
# Initialize git submodules
git submodule update --init --recursive

# Navigate to docs directory
cd docs

# Install npm dependencies (Bootstrap, Font Awesome, PostCSS)
npm install
```

### Running Locally

```bash
# Navigate to docs directory
cd docs

# Start development server
hugo server -D

# Open browser to http://localhost:1313
```

Server automatically reloads on file changes.

### Building for Production

```bash
cd docs

# Build with minification and correct base URL
hugo --minify -b "https://baretto-labs.github.io/OllamAssist/"

# Output is in docs/public/
```

## Project Structure

```
OllamAssist/
├── docs/                          # Documentation root
│   ├── config.yaml               # Hugo configuration
│   ├── README.md                 # Docs development guide
│   ├── .nojekyll                 # Disable Jekyll processing
│   │
│   ├── content/
│   │   └── en/                   # English content
│   │       ├── _index.md         # Homepage
│   │       └── docs/
│   │           ├── _index.md     # Docs intro
│   │           ├── getting-started/
│   │           │   └── _index.md
│   │           ├── features/
│   │           │   └── _index.md
│   │           ├── settings/
│   │           │   └── _index.md
│   │           └── faq/
│   │               └── _index.md
│   │
│   ├── static/
│   │   ├── images/               # Screenshots go here
│   │   │   └── README.md         # Screenshot naming guide
│   │   └── videos/               # Video tutorials go here
│   │       └── README.md         # Video guidelines
│   │
│   ├── themes/
│   │   └── docsy/                # Docsy theme (git submodule)
│   │
│   ├── layouts/                  # Custom layouts (optional)
│   ├── assets/                   # CSS/JS customizations (optional)
│   └── data/                     # Data files (optional)
│
├── .github/
│   └── workflows/
│       └── build-docs.yml        # GitHub Actions workflow
│
└── DOCUMENTATION_SETUP.md        # This file
```

## Adding Content

### Adding a New Page

1. Create markdown file:
   ```bash
   touch docs/content/en/docs/section-name/page-name.md
   ```

2. Add front matter:
   ```yaml
   ---
   title: "Page Title"
   description: "Short description for SEO"
   weight: 10
   ---

   # Content starts here
   ```

3. Lowest `weight` appears first in menu

### Supported Markdown

#### Code Blocks
````markdown
```java
public class Example {
    public static void main(String[] args) {
        System.out.println("Hello!");
    }
}
```
````

#### Alerts/Callouts
```markdown
{{< alert >}}
Important information here
{{< /alert >}}

{{< alert title="Warning" color="warning" >}}
Warning message
{{< /alert >}}
```

#### Tabs
```markdown
{{< tabpane langequals="yaml" >}}
{{% tab header="Option A" %}}
Content A
{{% /tab %}}
{{% tab header="Option B" %}}
Content B
{{% /tab %}}
{{< /tabpane >}}
```

See [Docsy Shortcodes](https://www.docsy.dev/docs/adding-content/shortcodes/) for more.

## Adding Media

### Screenshots

1. **Capture screenshot**
   - macOS: Cmd+Shift+4 (selection) or Cmd+Shift+5
   - Windows: Win+Shift+S
   - Linux: Gnome Screenshot or flameshot

2. **Optimize for web**
   - Recommended size: 1280x800 or 1920x1080
   - Format: PNG or JPG (PNG preferred)
   - Compress with [TinyPNG](https://tinypng.com/) if large

3. **Name and place**
   ```bash
   cp screenshot.png docs/static/images/01-plugin-installation.png
   ```

4. **Reference in documentation**
   ```markdown
   ![Plugin Installation](../images/01-plugin-installation.png)
   ```

See `docs/static/images/README.md` for detailed naming convention.

### Videos

1. **Create video**
   - Tools: OBS Studio (free), Camtasia, ScreenFlow
   - Format: MP4 (h.264 codec)
   - Resolution: 1280x720 or 1920x1080
   - Keep under 50MB

2. **Place in videos directory**
   ```bash
   cp video.mp4 docs/static/videos/setup-walkthrough.mp4
   ```

3. **Reference in documentation**
   ```markdown
   [Setup Walkthrough](../videos/setup-walkthrough.mp4) - 10 minutes
   ```

See `docs/static/videos/README.md` for recommended videos.

## GitHub Pages Setup

### Initial Configuration

1. **Go to repository settings**
   - GitHub.com → Your Repo → Settings → Pages

2. **Configure Pages source**
   - Source: Deploy from a branch
   - Branch: `gh-pages`
   - Folder: `/ (root)`
   - Click "Save"

3. **GitHub Action handles the rest**
   - Workflow: `.github/workflows/build-docs.yml`
   - Triggers on push to `main` (docs/ changes)
   - Builds Hugo site
   - Deploys to `gh-pages` branch
   - Available at: `https://baretto-labs.github.io/OllamAssist/`

### Deployment Workflow

```
Push to main
    ↓
GitHub Action Triggered
    ↓
Hugo Builds Documentation
    ↓
Output pushed to gh-pages
    ↓
GitHub Pages serves content
    ↓
Live at https://baretto-labs.github.io/OllamAssist/
```

Typical time: 30-60 seconds

### Troubleshooting Deployment

**Check deployment status:**
1. Go to repo → **Actions** tab
2. Click **Build and Deploy Docs** workflow
3. Check recent runs for status/errors

**Common issues:**

- **Workflow not running:** Check paths in `build-docs.yml` (should be `docs/**`)
- **Pages not updating:** Clear browser cache or wait 5 minutes
- **Build failed:** Check error in Actions logs

## Theme Customization

### Colors & Branding

Edit `docs/config.yaml`:
```yaml
params:
  logo:
    text: "OllamAssist"
    url: "/"

  social:
    github: "https://github.com/baretto-labs/OllamAssist"
    twitter: "https://twitter.com/..."
```

### Menu Configuration

```yaml
menu:
  main:
    - name: "Documentation"
      weight: 10
      url: "/docs/"
    - name: "Features"
      weight: 20
      url: "/docs/features/"
    - name: "GitHub"
      weight: 100
      url: "https://github.com/baretto-labs/OllamAssist"
```

Lower `weight` appears first.

### Custom Styles

Create `docs/assets/css/custom.css`:
```css
:root {
  --bs-primary: #3498db;
  --bs-secondary: #95a5a6;
}
```

See [Docsy Customization](https://www.docsy.dev/docs/adding-content/lookandfeel/) for more.

## Maintenance

### Regular Tasks

**After releases:**
1. Update version numbers in documentation
2. Add new features to `docs/features/`
3. Update FAQs if needed
4. Create video if complex new feature

**Monthly:**
1. Review analytics (if enabled)
2. Check for broken links
3. Update screenshots if UI changed

### Checking for Broken Links

```bash
cd docs
hugo --buildFutures --buildDrafts

# Use a link checker tool on local site
# Or after deployment, use tools like:
# - broken-link-checker (npm)
# - htmlproofer (ruby)
```

### Updating Theme

The Docsy theme is included as a submodule. To update:

```bash
cd docs/themes/docsy
git pull origin main
cd ../..
git add docs/themes/docsy
git commit -m "Update Docsy theme"
```

## Development Workflow

### Creating New Documentation

1. **Create feature branch**
   ```bash
   git checkout -b docs/new-feature
   ```

2. **Make changes**
   ```bash
   # Add content
   nano docs/content/en/docs/features/new-feature.md

   # Test locally
   cd docs
   hugo server -D
   ```

3. **Verify in browser**
   - Visit `http://localhost:1313`
   - Check rendering
   - Check links
   - Check images/videos load

4. **Commit and push**
   ```bash
   git add docs/
   git commit -m "docs: Add documentation for new feature"
   git push origin docs/new-feature
   ```

5. **Create pull request**
   - GitHub → Compare & pull request
   - Include screenshots/videos in PR
   - Request review

6. **After merge**
   - Wait for GitHub Action to complete
   - Verify at `https://baretto-labs.github.io/OllamAssist/`

## Best Practices

### Writing

- ✅ Clear, concise sections
- ✅ Step-by-step instructions
- ✅ Screenshots for visual steps
- ✅ Examples with code blocks
- ✅ Links to related topics
- ❌ Avoid jargon
- ❌ Don't assume technical knowledge

### Organization

- ✅ Logical hierarchy
- ✅ Related content grouped
- ✅ Consistent naming
- ✅ Cross-references
- ❌ Don't make sections too deep (3 levels max)
- ❌ Avoid orphaned pages

### Accessibility

- ✅ Alt text for images
- ✅ Descriptive link text
- ✅ Semantic heading hierarchy
- ✅ High contrast
- ❌ Don't use color alone to convey meaning

## Resources

- [Hugo Documentation](https://gohugo.io/documentation/)
- [Docsy Theme Guide](https://www.docsy.dev/docs/)
- [Markdown Guide](https://www.markdownguide.org/)
- [GitHub Pages Guide](https://docs.github.com/en/pages)

## Checklist: Setting Up Docs

- [ ] Hugo installed and version v0.100+
- [ ] Run locally with `hugo server -D`
- [ ] GitHub Pages source configured (gh-pages branch)
- [ ] GitHub Action workflow in place
- [ ] Test deployment to GitHub Pages
- [ ] Add screenshots to `docs/static/images/`
- [ ] Add videos to `docs/static/videos/`
- [ ] Configure menu in `config.yaml`
- [ ] Test all links work
- [ ] Verify search works
- [ ] Test on mobile
- [ ] Share with team

## Support

For issues or questions:
- [Hugo Community](https://discourse.gohugo.io/)
- [Docsy Discussions](https://github.com/google/docsy/discussions)
- [GitHub Pages Support](https://support.github.com/categories/github-pages-basics)
