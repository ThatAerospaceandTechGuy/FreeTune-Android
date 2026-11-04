# FreeTune website

Static marketing and download site, deployed to Cloudflare Pages at
<https://freetunes.pages.dev>.

## Build

```sh
node site/build.mjs
```

The build reads the real APKs and checksums from `../dist`, writes the page into
`site/public`, and copies every APK into `site/public/downloads`. Set `SITE_URL`
to change the canonical URL:

```sh
SITE_URL=https://freetunes.pages.dev node site/build.mjs
```

`site/public` is generated and ignored by git; `site/src` is the source of truth.
Assets in `site/public` are uploaded as-is by Wrangler, including the APKs copied
from `dist/`.

## Preview locally

```sh
npx serve site/public
```

## Deploy

The connected Cloudflare account is used through `CLOUDFLARE_API_TOKEN`:

```sh
npx wrangler pages deploy site/public --project-name freetunes --branch main
```

Wrangler uploads the directory directly; there is no Git integration or build
server involved. The project was created with the connected Cloudflare account.

Pages serves `_headers` (APK content type, security headers) and `_redirects`
(`/latest` and `/v/<version>` short links) from the build output.

## Adding a release

1. Build the APK into `dist/` and update `dist/SHA256.txt`.
2. Run `node site/build.mjs`.
3. Deploy again.

The version table, file sizes and checksums come straight from `dist/`, so the
site cannot drift from the shipped binaries.
