#!/usr/bin/env node

import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';

const [distDirArg, gatewayOriginArg, portArg] = process.argv.slice(2);

if (!distDirArg || !gatewayOriginArg || !portArg) {
  console.error('Usage: node scripts/seller-web-proxy.mjs <dist-dir> <gateway-origin> <port>');
  process.exit(1);
}

const distDir = path.resolve(distDirArg);
const gatewayOrigin = gatewayOriginArg.replace(/\/$/, '');
const port = Number(portArg);

if (!Number.isInteger(port) || port <= 0) {
  console.error(`Invalid port: ${portArg}`);
  process.exit(1);
}

const contentTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'application/javascript; charset=utf-8'],
  ['.wasm', 'application/wasm'],
  ['.map', 'application/json; charset=utf-8'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8']
]);

const server = http.createServer(async (req, res) => {
  try {
    if (!req.url) {
      res.writeHead(400);
      res.end('Missing request URL');
      return;
    }

    const requestUrl = new URL(req.url, `http://127.0.0.1:${port}`);
    if (requestUrl.pathname.startsWith('/api/') || requestUrl.pathname.startsWith('/auth/')) {
      await proxyRequest(req, res, requestUrl);
      return;
    }

    await serveStatic(res, requestUrl.pathname);
  } catch (error) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end(error instanceof Error ? error.message : String(error));
  }
});

server.listen(port, '127.0.0.1', () => {
  console.log(`Seller web proxy listening on http://127.0.0.1:${port}`);
});

async function proxyRequest(req, res, requestUrl) {
  const targetUrl = new URL(requestUrl.pathname + requestUrl.search, gatewayOrigin);
  const body = await readBody(req);
  const headers = new Headers();

  for (const [name, value] of Object.entries(req.headers)) {
    if (!value || name.toLowerCase() === 'host') {
      continue;
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        headers.append(name, item);
      }
    } else {
      headers.set(name, value);
    }
  }

  const upstream = await fetch(targetUrl, {
    method: req.method,
    headers,
    body: req.method === 'GET' || req.method === 'HEAD' ? undefined : body,
    redirect: 'manual'
  });

  const responseHeaders = {};
  upstream.headers.forEach((value, name) => {
    responseHeaders[name] = value;
  });
  res.writeHead(upstream.status, responseHeaders);
  if (req.method === 'HEAD') {
    res.end();
    return;
  }
  const responseBody = Buffer.from(await upstream.arrayBuffer());
  res.end(responseBody);
}

async function serveStatic(res, pathname) {
  const relativePath = pathname === '/' ? '/index.html' : pathname;
  const absolutePath = path.resolve(distDir, `.${relativePath}`);
  if (!absolutePath.startsWith(distDir)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  let filePath = absolutePath;
  try {
    const stats = await fs.stat(filePath);
    if (stats.isDirectory()) {
      filePath = path.join(filePath, 'index.html');
    }
  } catch {
    filePath = path.join(distDir, 'index.html');
  }

  const content = await fs.readFile(filePath);
  const contentType = contentTypes.get(path.extname(filePath)) ?? 'application/octet-stream';
  res.writeHead(200, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store'
  });
  res.end(content);
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
