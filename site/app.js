(function () {
  "use strict";

  var REPO = "https://github.com/00JackLu/qupath-mcp.git";
  var RELEASES = "https://github.com/00JackLu/qupath-mcp/releases/latest";
  var HOST = "127.0.0.1";

  var state = {
    os: /Win/.test(navigator.platform || "") ? "win" : "unix",
    method: "jar",
    port: 9527,
    capture: false,
    noScripts: false,
    noPlugins: false,
    noFiles: false,
    client: "claude-code"
  };

  function store(key, value) {
    try { localStorage.setItem("qupath-mcp:" + key, value); } catch (e) { /* storage unavailable */ }
  }
  function load(key) {
    try { return localStorage.getItem("qupath-mcp:" + key); } catch (e) { return null; }
  }

  function url() { return "http://" + HOST + ":" + state.port + "/mcp"; }
  function json(obj) { return JSON.stringify(obj, null, 2); }

  // ---------- DOM helpers ----------

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        if (k === "text") node.textContent = attrs[k];
        else if (k === "html") node.innerHTML = attrs[k];
        else node.setAttribute(k, attrs[k]);
      });
    }
    (children || []).forEach(function (c) { if (c) node.appendChild(c); });
    return node;
  }

  function code(text, label) {
    return el("div", { "class": "code" }, [
      label ? el("div", { "class": "code-label", text: label }) : null,
      el("div", { "class": "code-box" }, [el("pre", { text: text })])
    ]);
  }

  function para(html) { return el("p", { html: html }); }
  function note(html, warn) {
    return el("div", { "class": warn ? "note warn" : "note" }, [el("p", { html: html })]);
  }

  function fill(container, nodes) {
    container.innerHTML = "";
    nodes.forEach(function (n) { if (n) container.appendChild(n); });
    addCopyButtons(container);
  }

  function addCopyButtons(root) {
    root.querySelectorAll(".code-box").forEach(function (box) {
      if (box.querySelector(".copy")) return;
      var btn = el("button", { "class": "copy", type: "button", text: "Copy" });
      btn.addEventListener("click", function () {
        var text = box.querySelector("pre").textContent;
        copyText(text).then(function () {
          btn.textContent = "Copied";
          btn.classList.add("done");
          setTimeout(function () { btn.textContent = "Copy"; btn.classList.remove("done"); }, 1500);
        });
      });
      box.appendChild(btn);
    });
  }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).catch(function () { return fallbackCopy(text); });
    }
    return Promise.resolve(fallbackCopy(text));
  }
  function fallbackCopy(text) {
    var ta = el("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand("copy"); } catch (e) { /* nothing more to try */ }
    document.body.removeChild(ta);
  }

  // ---------- Settings → env vars / properties ----------

  function settings() {
    // [env var, properties key, value]; only non-defaults beyond the launch trio
    var s = [
      ["QUPATH_MCP_TRANSPORT", "transport", "HTTP"],
      ["QUPATH_MCP_PORT", "port", String(state.port)],
      ["QUPATH_MCP_AUTO_START", "autoStart", "true"]
    ];
    if (state.capture) s.push(["QUPATH_MCP_ENABLE_CAPTURE_VIEW", "enableCaptureView", "true"]);
    if (state.noScripts) s.push(["QUPATH_MCP_ENABLE_SCRIPTS", "enableScripts", "false"]);
    if (state.noPlugins) s.push(["QUPATH_MCP_ENABLE_PLUGINS", "enablePlugins", "false"]);
    if (state.noFiles) s.push(["QUPATH_MCP_ENABLE_FILE_ACCESS", "enableFileAccess", "false"]);
    return s;
  }

  function hasNonDefaults() {
    return state.port !== 9527 || state.capture || state.noScripts || state.noPlugins || state.noFiles;
  }

  function cloneCmd() {
    return "git clone " + REPO + "\ncd qupath-mcp";
  }

  function gradlew() { return state.os === "win" ? ".\\gradlew.bat" : "./gradlew"; }

  function propertiesFile() {
    return settings()
      .filter(function (p) { return p[1] !== "transport" && p[1] !== "autoStart" && !(p[1] === "port" && state.port === 9527); })
      .map(function (p) { return p[1] + "=" + p[2]; })
      .join("\n");
  }

  function propertiesPath() {
    return state.os === "win" ? "%USERPROFILE%\\.qupath\\mcp.properties" : "~/.qupath/mcp.properties";
  }

  // ---------- Step 2: install ----------

  function renderInstall() {
    var box = document.getElementById("step-install");
    var dragAndDrop = "Drag the JAR onto the QuPath window, or copy it into the extensions directory shown under " +
      "<strong>Extensions ▸ Manage extensions</strong>. Then restart QuPath; the MCP server starts automatically.";
    var nodes = state.method === "source" ? [
      el("h3", { text: "Build the JAR from source" }),
      code(cloneCmd() + "\n" + gradlew() + " build", "Build the JAR"),
      para("The first build downloads dependencies and the JDK 25 toolchain, so it can take a few minutes. " +
        "The JAR lands at <code>build/libs/qupath-extension-mcp-&lt;version&gt;.jar</code>. " + dragAndDrop)
    ] : [
      el("h3", { text: "Install the released JAR" }),
      para("Download <code>qupath-extension-mcp-&lt;version&gt;.jar</code> from the " +
        "<a href=\"" + RELEASES + "\">latest release</a>. " + dragAndDrop)
    ];
    nodes.push(note("Works with QuPath <strong>0.7.0</strong>. Keep only one copy of the JAR in the extensions directory; " +
      "if you replace it, fully quit and restart QuPath so the new classes load.", true));
    if (hasNonDefaults()) {
      nodes.push(para("QuPath started from the Dock, Start menu or Finder usually doesn't see shell environment " +
        "variables, so put your settings in a properties file:"));
      nodes.push(code(propertiesFile(), propertiesPath()));
    } else {
      nodes.push(para("The defaults (HTTP on port 9527, auto-start on launch) need no configuration. " +
        "Change a setting above to generate a properties file."));
    }
    fill(box, nodes);
  }

  // ---------- Step 3: verify ----------

  function renderVerify() {
    var health = "http://" + HOST + ":" + state.port + "/health";
    var listBody = '{"jsonrpc":"2.0","id":1,"method":"tools/list"}';
    var text;
    if (state.os === "win") {
      text = "curl.exe " + health + "\n# -> {\"status\":\"ok\"}\n\n" +
        "Invoke-RestMethod -Uri " + url() + " -Method Post -ContentType 'application/json' `\n" +
        "  -Body '" + listBody + "' | ConvertTo-Json -Depth 6";
    } else {
      text = "curl " + health + "\n# -> {\"status\":\"ok\"}\n\n" +
        "curl -s -H 'Content-Type: application/json' \\\n" +
        "  -d '" + listBody + "' \\\n" +
        "  " + url();
    }
    var pre = document.querySelector('[data-snippet="verify"]');
    pre.textContent = text;
    addCopyButtons(pre.parentNode.parentNode);

    var smoke = (state.port === 9527 ? "" : "PORT=" + state.port + " ") + "./test-mcp-server.sh";
    fill(document.getElementById("verify-extra"), [
      para("For a full end-to-end check, run the bundled smoke test from the repo root. It prints PASS/FAIL per check" +
        (state.os === "win" ? " and needs a bash shell (Git Bash or WSL)." : ".")),
      code(smoke)
    ]);
  }

  // ---------- Connect ----------

  var CLIENTS = {
    "claude-code": function () {
      return [
        para("Claude Code supports HTTP MCP servers natively. Add the server with one command:"),
        code("claude mcp add --transport http qupath " + url()),
        para("Or commit it to a project's <code>.mcp.json</code> so everyone on the project gets it:"),
        code(json({ mcpServers: { qupath: { type: "http", url: url() } } }), ".mcp.json (project root)"),
        para("With QuPath running, <code>claude mcp list</code> should show <code>qupath: … ✔ Connected</code>.")
      ];
    },
    "claude-desktop": function () {
      var path = state.os === "win"
        ? "%APPDATA%\\Claude\\claude_desktop_config.json"
        : "~/Library/Application Support/Claude/claude_desktop_config.json";
      return [
        para("Claude Desktop connects through the <code>mcp-remote</code> stdio bridge, which needs " +
          "<a href=\"https://nodejs.org/\">Node.js</a> for <code>npx</code>. Open " +
          "<strong>Settings ▸ Developer ▸ Edit Config</strong>, or edit the file directly:"),
        code(json({ mcpServers: { qupath: { command: "npx", args: ["-y", "mcp-remote", url()] } } }), path),
        para("If the file already has other servers, add <code>qupath</code> inside the existing <code>mcpServers</code> " +
          "object. Restart Claude Desktop afterwards.")
      ];
    },
    "codex": function () {
      var path = state.os === "win" ? "%USERPROFILE%\\.codex\\config.toml" : "~/.codex/config.toml";
      return [
        para("Through the <code>mcp-remote</code> stdio bridge, which works on all Codex versions (needs Node.js):"),
        code("[mcp_servers.qupath]\ncommand = \"npx\"\nargs = [\"-y\", \"mcp-remote\", \"" + url() + "\"]", path),
        para("Recent Codex builds can connect to the HTTP endpoint directly instead:"),
        code("[mcp_servers.qupath]\nurl = \"" + url() + "\"\nexperimental_use_rmcp_client = true", path)
      ];
    },
    "cursor": function () {
      return [
        para("Cursor connects to HTTP MCP servers directly. Add the server globally or per project:"),
        code(json({ mcpServers: { qupath: { url: url() } } }),
          (state.os === "win" ? "%USERPROFILE%\\.cursor\\mcp.json" : "~/.cursor/mcp.json") + "  (or .cursor/mcp.json in a project)"),
        para("Then check <strong>Cursor Settings ▸ MCP</strong> shows <code>qupath</code> with its tools.")
      ];
    },
    "vscode": function () {
      return [
        para("VS Code (GitHub Copilot agent mode) reads MCP servers from <code>.vscode/mcp.json</code>. " +
          "Note the top-level key is <code>servers</code>, not <code>mcpServers</code>:"),
        code(json({ servers: { qupath: { type: "http", url: url() } } }), ".vscode/mcp.json"),
        para("Or run <strong>MCP: Add Server…</strong> from the Command Palette and choose HTTP.")
      ];
    },
    "other": function () {
      return [
        para("Clients that support Streamable HTTP can use the endpoint directly. The common config shape is:"),
        code(json({ qupath: { type: "http", url: url() } })),
        para("For stdio-only clients, run the <code>mcp-remote</code> bridge as the server command:"),
        code("npx -y mcp-remote " + url()),
        para("Any HTTP client can talk JSON-RPC to the endpoint, which is handy for debugging:"),
        code(state.os === "win"
          ? "Invoke-RestMethod -Uri " + url() + " -Method Post -ContentType 'application/json' `\n" +
            "  -Body '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}'"
          : "curl -s -H 'Content-Type: application/json' \\\n" +
            "  -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}' \\\n" +
            "  " + url())
      ];
    }
  };

  function renderClient() {
    document.querySelectorAll("[data-client]").forEach(function (b) {
      b.setAttribute("aria-selected", String(b.dataset.client === state.client));
    });
    fill(document.getElementById("client-panel"), CLIENTS[state.client]());
  }

  // ---------- Wiring ----------

  function renderAll() {
    document.querySelectorAll(".seg[data-control]").forEach(function (seg) {
      var key = seg.dataset.control;
      seg.querySelectorAll("button").forEach(function (b) {
        b.setAttribute("aria-pressed", String(b.dataset.value === state[key]));
      });
    });
    document.getElementById("hero-endpoint").textContent = url();
    renderInstall();
    renderVerify();
    renderClient();
  }

  document.querySelectorAll(".seg[data-control]").forEach(function (seg) {
    seg.addEventListener("click", function (e) {
      var b = e.target.closest("button");
      if (!b) return;
      state[seg.dataset.control] = b.dataset.value;
      store(seg.dataset.control, b.dataset.value);
      renderAll();
    });
  });

  var portInput = document.getElementById("port");
  portInput.addEventListener("input", function () {
    var n = parseInt(portInput.value, 10);
    if (n >= 1 && n <= 65535) { state.port = n; renderAll(); }
  });

  [["opt-capture", "capture"], ["opt-noscripts", "noScripts"], ["opt-noplugins", "noPlugins"], ["opt-nofiles", "noFiles"]]
    .forEach(function (pair) {
      document.getElementById(pair[0]).addEventListener("change", function (e) {
        state[pair[1]] = e.target.checked;
        renderAll();
      });
    });

  document.querySelectorAll("[data-client]").forEach(function (b) {
    b.addEventListener("click", function () {
      state.client = b.dataset.client;
      store("client", state.client);
      renderClient();
    });
  });

  // Theme toggle: auto → light → dark → auto
  var themeBtn = document.getElementById("theme-btn");
  function applyTheme(t) {
    if (t === "light" || t === "dark") document.documentElement.setAttribute("data-theme", t);
    else document.documentElement.removeAttribute("data-theme");
    themeBtn.textContent = t === "light" ? "Light" : t === "dark" ? "Dark" : "Auto";
  }
  var theme = load("theme") || "auto";
  applyTheme(theme);
  themeBtn.addEventListener("click", function () {
    theme = theme === "auto" ? "light" : theme === "light" ? "dark" : "auto";
    store("theme", theme);
    applyTheme(theme);
  });

  // Browsers may restore form values on reload, so start from what the inputs show
  state.port = parseInt(portInput.value, 10) || 9527;
  state.capture = document.getElementById("opt-capture").checked;
  state.noScripts = document.getElementById("opt-noscripts").checked;
  state.noPlugins = document.getElementById("opt-noplugins").checked;
  state.noFiles = document.getElementById("opt-nofiles").checked;

  ["os", "method", "client"].forEach(function (k) {
    var v = load(k);
    if (v) state[k] = v;
  });
  if (!CLIENTS[state.client]) state.client = "claude-code";

  addCopyButtons(document);
  renderAll();
})();
