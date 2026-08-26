# BYD Trip Stats — marketing website

A self-contained static landing page (no build step, no framework) for advertising
the app. Plain HTML + CSS + vanilla JS, styled to match the app's DiLink "Ocean
Series" palette.

```
website/
├── index.html              # the landing page
├── 404.html                # custom not-found page
├── styles.css              # all styling
├── script.js               # nav, scroll-reveal, screenshot placeholders
├── _headers                # Cloudflare Pages HTTP headers (security + caching)
└── assets/
    ├── logo.png            # app launcher icon (logo + favicon)
    └── screenshots/        # ← drop your screenshots here (see its README)
```

## Adding screenshots

See [`assets/screenshots/README.md`](assets/screenshots/README.md). Each frame shows
the exact filename it expects; drop a matching image in and it appears automatically.

## Preview locally

Any static server works, e.g.:

```bash
cd website
python3 -m http.server 8080
# open http://localhost:8080
```

## Deploy to Cloudflare Pages

This is a pure static site, so there is **no build command**.

### Option A — Git integration (recommended)

1. Push this repo to GitHub/GitLab.
2. Cloudflare dashboard → **Workers & Pages → Create → Pages → Connect to Git**.
3. Select the repo, then set:
   - **Framework preset:** `None`
   - **Build command:** *(leave empty)*
   - **Build output directory:** `website`
4. Deploy. Every push to the production branch redeploys automatically.

### Option B — Direct upload with Wrangler

```bash
# one-time: npm i -g wrangler  (and `wrangler login`)
npx wrangler pages deploy website --project-name byd-trip-stats
```

### Custom domain

Pages → your project → **Custom domains** → add your domain and follow the DNS steps.
Until then the site is live at `https://<project>.pages.dev`.

> After choosing the final URL, update the `og:url` and `og:image` absolute paths in
> `index.html` if you want richer social-share previews.
