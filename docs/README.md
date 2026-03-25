# OllamAssist Documentation

This is the Hugo documentation site for OllamAssist. It uses the [Docsy](https://www.docsy.dev/) theme.

## Structure

```
docs/
├── config.yaml           # Hugo configuration
├── content/
│   └── en/
│       ├── _index.md     # Homepage
│       └── docs/
│           ├── getting-started/
│           ├── features/
│           ├── settings/
│           └── faq/
├── static/
│   ├── images/           # Screenshots (add here)
│   └── videos/           # Video tutorials (add here)
└── themes/
    └── docsy/            # Docsy theme (git submodule)
```

## Building Locally

### Prerequisites
- Hugo (extended version, v0.100+)
  ```bash
  brew install hugo  # macOS
  choco install hugo-extended  # Windows
  sudo apt-get install hugo  # Linux
  ```
- Node.js and npm (v18+)
  ```bash
  # For macOS
  brew install node

  # Verify installation
  node --version
  npm --version
  ```
- Git submodules initialized
  ```bash
  git submodule update --init --recursive
  ```

### Installation

```bash
cd docs

# Install npm dependencies
npm install
```

This installs Bootstrap and Font Awesome required by the Docsy theme.

### Development Server

```bash
cd docs
hugo server -D
```

Then visit `http://localhost:1313` in your browser.

The server will automatically reload when you make changes.

### Production Build

```bash
cd docs

# Build with minification
hugo --minify -b "https://baretto-labs.github.io/OllamAssist/"
```

Output is in `docs/public/`

## Content Structure

### Adding Pages

1. Create a new markdown file in the appropriate section:
   ```bash
   # New page in getting-started
   docs/content/en/docs/getting-started/page-name.md

   # New page in features
   docs/content/en/docs/features/page-name.md
   ```

2. Front matter template:
   ```yaml
   ---
   title: "Page Title"
   description: "Short description for search results"
   weight: 10
   ---
   ```

3. Lower `weight` = higher in menu order

### Front Matter Options

```yaml
---
title: "Page Title"                    # Required
description: "SEO description"         # Optional but recommended
weight: 10                             # Menu order
draft: false                           # Set to true to hide while editing
---
```

## Adding Media

### Screenshots

1. Take a screenshot
2. Save as PNG or JPG in `static/images/`
3. Reference in markdown:
   ```markdown
   ![Description of image](../images/image-name.png)
   ```

See `static/images/README.md` for naming convention and guidelines.

### Videos

1. Save video as MP4 in `static/videos/`
2. Reference in markdown:
   ```markdown
   [Video Title](../videos/video-name.mp4)
   ```

See `static/videos/README.md` for recommended videos and guidelines.

## Markdown Features

The documentation supports extended markdown:

### Code Blocks
````markdown
```java
public class Example {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```
````

### Alerts/Callouts
```markdown
{{< alert >}}
This is a note. Use {{< /alert >}} to close.
```

Types: `alert`, `warning`, `note`, `tip`

### Tabs
```markdown
{{< tabpane langequals="yaml" >}}
{{% tab header="Config A" %}}
content for tab A
{{% /tab %}}
{{% tab header="Config B" %}}
content for tab B
{{% /tab %}}
{{< /tabpane >}}
```

### Columns
```markdown
{{< blocks/section color="primary" >}}
{{% blocks/feature icon="fas fa-rocket" title="Feature" %}}
Feature description
{{% /blocks/feature %}}
{{< /blocks/section >}}
```

## Deployment

### GitHub Pages Setup

The documentation is automatically deployed to GitHub Pages when you push to `main`.

To enable:

1. Go to repository **Settings > Pages**
2. Select **Source: Deploy from a branch**
3. Branch: `gh-pages`
4. Save

The GitHub Action `.github/workflows/build-docs.yml` handles:
- Building the Hugo site
- Creating `gh-pages` branch with built content
- Deploying to GitHub Pages

**Note:** The first deployment may take a few minutes.

### Deployment URL

The docs will be available at:
```
https://baretto-labs.github.io/OllamAssist/
```

## Themes & Styling

OllamAssist docs use the [Docsy](https://www.docsy.dev/) theme which provides:

- Responsive design
- Built-in search
- Dark mode support
- Sidebar navigation
- Social links
- Edit on GitHub links

### Color Customization

Edit `docs/config.yaml` to change:
```yaml
params:
  logo:
    text: "OllamAssist"
  social:
    github: "https://github.com/baretto-labs/OllamAssist"
```

### Theme Documentation

See [Docsy Documentation](https://www.docsy.dev/docs/) for:
- Advanced Shortcodes
- Customization
- Navigation configuration
- Search setup

## Editing Tips

### Navigation

The main menu is configured in `config.yaml`:
```yaml
menu:
  main:
    - name: "Documentation"
      weight: 10
      url: "/docs/"
```

Lower weight appears first.

### Search

Search is built-in and searches:
- Page titles
- Headings
- Content
- Front matter descriptions

No additional setup needed.

### SEO

For better search results:
1. Set meaningful `title` and `description` in front matter
2. Use descriptive headings (H2, H3)
3. Include `weight` for proper ordering
4. Use alt text for images

## Contributing

To contribute to documentation:

1. Create a new branch
2. Edit or create markdown files
3. Test locally: `hugo server -D`
4. Submit a pull request

## Troubleshooting

### Site won't build

```bash
# Clear cache
rm -rf docs/resources/

# Try again
cd docs
hugo --minify
```

### Theme not loading

```bash
# Ensure submodule is initialized
git submodule update --init --recursive

# Or manually clone theme
cd docs
git clone https://github.com/google/docsy.git themes/docsy
```

### Port already in use

```bash
# Use different port
hugo server -p 1314
```

### Deployment not working

Check GitHub Actions:
1. Go to **Actions** tab
2. Click **Build and Deploy Docs** workflow
3. Check recent runs for errors
4. Review logs

## Resources

- [Hugo Documentation](https://gohugo.io/documentation/)
- [Docsy Theme Guide](https://www.docsy.dev/docs/)
- [Markdown Syntax](https://www.markdownguide.org/)
- [GitHub Pages Guide](https://docs.github.com/en/pages)

## Next Steps

1. ✅ Structure created
2. ⏳ Add screenshots to `static/images/`
3. ⏳ Create videos in `static/videos/`
4. ⏳ Test locally with `hugo server`
5. ⏳ Push to `main` to trigger deployment
6. ⏳ Verify at `https://baretto-labs.github.io/OllamAssist/`
