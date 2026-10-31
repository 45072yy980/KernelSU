window.__ModuleLoader__.load({
	id: "dsha-diksu-git",
	factory: (require) => {
		var module = { exports: {} };
		var exports = module.exports;
		Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
		let react = require("react");
		let react_jsx_runtime = require("react/jsx-runtime");

		// ────────────────────────── token 配置页 ──────────────────────────
		//
		// DSH 的「设置 → 插件」只提供 slot，不生成配置表单，所以这一页由插件自己贡献。
		// 保存走 host 的 /__dsha/diksu-git/token（同一鉴权链路），落盘到 ~/.dsh/git-token，
		// 与运行时读取的是同一个文件，因此保存后立即生效、无需重启。
		const ENDPOINT = "/__dsha/diksu-git/token";

		const s = {
			wrap: { display: "flex", flexDirection: "column", gap: "14px", maxWidth: "640px" },
			card: {
				display: "flex", flexDirection: "column", gap: "10px", padding: "14px",
				border: "1px solid var(--dsw-alias-border-l2, rgba(128,128,128,.25))",
				borderRadius: "10px",
			},
			row: { display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" },
			label: { fontSize: "13px", fontWeight: 600 },
			desc: { fontSize: "12px", lineHeight: "18px", opacity: 0.72, margin: 0 },
			code: {
				fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", fontSize: "11.5px",
				background: "var(--dsw-alias-bg-l2, rgba(128,128,128,.12))", padding: "1px 5px", borderRadius: "4px",
			},
			input: {
				flex: "1 1 260px", minWidth: "200px", padding: "8px 10px", fontSize: "13px",
				borderRadius: "8px", border: "1px solid var(--dsw-alias-border-l2, rgba(128,128,128,.3))",
				background: "var(--dsw-alias-bg-l1, transparent)", color: "inherit", fontFamily: "inherit",
			},
			btn: {
				padding: "8px 14px", fontSize: "13px", borderRadius: "8px", cursor: "pointer",
				border: "1px solid var(--dsw-alias-border-l2, rgba(128,128,128,.3))",
				background: "var(--dsw-alias-bg-l1, transparent)", color: "inherit", fontFamily: "inherit",
			},
			btnPrimary: {
				padding: "8px 14px", fontSize: "13px", borderRadius: "8px", cursor: "pointer",
				border: "1px solid transparent", fontFamily: "inherit",
				background: "var(--dsw-alias-state-business-primary, #4d6bfe)", color: "#fff",
			},
			badge: (ok) => ({
				display: "inline-block", padding: "2px 8px", borderRadius: "999px", fontSize: "12px",
				color: ok ? "#0a7d32" : "#a15c00",
				background: ok ? "rgba(10,125,50,.12)" : "rgba(161,92,0,.12)",
			}),
			msg: (ok) => ({ fontSize: "12.5px", color: ok ? "#0a7d32" : "#c0392b", margin: 0, whiteSpace: "pre-wrap" }),
		};

		async function request(init) {
			const response = await fetch(ENDPOINT, { credentials: "same-origin", ...init });
			let payload = {};
			try {
				payload = await response.json();
			} catch {
				payload = { ok: false, error: `HTTP ${response.status}` };
			}
			if (!response.ok || payload.ok === false) {
				throw new Error(payload.error || `HTTP ${response.status}`);
			}
			return payload;
		}

		function DiksuGitTokenSettings() {
			const [status, setStatus] = react.useState({ loading: true, configured: false, source: "", file: "" });
			const [token, setToken] = react.useState("");
			const [busy, setBusy] = react.useState(false);
			const [message, setMessage] = react.useState(null);
			const [reveal, setReveal] = react.useState(false);

			const load = react.useCallback(async () => {
				try {
					const payload = await request({ method: "GET" });
					setStatus({ loading: false, configured: payload.configured === true, source: payload.source || "", file: payload.file || "" });
				} catch (error) {
					setStatus({ loading: false, configured: false, source: "", file: "" });
					setMessage({ ok: false, text: `读取状态失败：${error.message}` });
				}
			}, []);

			react.useEffect(() => {
				load();
			}, [load]);

			const save = async () => {
				const value = token.trim();
				if (value.length === 0) {
					setMessage({ ok: false, text: "先粘贴 token 再保存" });
					return;
				}
				setBusy(true);
				setMessage(null);
				try {
					const payload = await request({
						method: "POST",
						headers: { "content-type": "application/json" },
						body: JSON.stringify({ token: value }),
					});
					setToken("");
					setMessage({ ok: true, text: `已保存到 ${payload.file}（立即生效，不用重启）` });
					await load();
				} catch (error) {
					setMessage({ ok: false, text: `保存失败：${error.message}` });
				} finally {
					setBusy(false);
				}
			};

			const clear = async () => {
				setBusy(true);
				setMessage(null);
				try {
					const payload = await request({
						method: "POST",
						headers: { "content-type": "application/json" },
						body: JSON.stringify({ clear: true }),
					});
					setMessage({ ok: true, text: payload.cleared ? "已清除插件保存的 token" : "文件本来就不存在" });
					await load();
				} catch (error) {
					setMessage({ ok: false, text: `清除失败：${error.message}` });
				} finally {
					setBusy(false);
				}
			};

			return react_jsx_runtime.jsxs("div", {
				style: s.wrap,
				children: [
					react_jsx_runtime.jsxs("div", {
						style: s.card,
						children: [
							react_jsx_runtime.jsxs("div", {
								style: s.row,
								children: [
									react_jsx_runtime.jsx("span", { style: s.label, children: "git token 状态" }),
									status.loading
										? react_jsx_runtime.jsx("span", { style: s.badge(false), children: "读取中…" })
										: react_jsx_runtime.jsx("span", {
												style: s.badge(status.configured),
												children: status.configured ? "已配置" : "未配置",
											}),
								],
							}),
							react_jsx_runtime.jsx("p", {
								style: s.desc,
								children: status.configured
									? `当前来源：${status.source}`
									: "推送、同步、触发 CI、发布 release 都需要它。公开仓库只读可以不用。",
							}),
							status.file
								? react_jsx_runtime.jsxs("p", {
										style: s.desc,
										children: ["保存位置：", react_jsx_runtime.jsx("code", { style: s.code, children: status.file })],
									})
								: null,
						],
					}),
					react_jsx_runtime.jsxs("div", {
						style: s.card,
						children: [
							react_jsx_runtime.jsx("span", { style: s.label, children: "粘贴 GitHub PAT" }),
							react_jsx_runtime.jsxs("div", {
								style: s.row,
								children: [
									react_jsx_runtime.jsx("input", {
										style: s.input,
										type: reveal ? "text" : "password",
										placeholder: "ghp_… 或 github_pat_…",
										value: token,
										autoComplete: "off",
										spellCheck: false,
										onChange: (event) => setToken(event.target.value),
										onKeyDown: (event) => {
											if (event.key === "Enter") save();
										},
									}),
									react_jsx_runtime.jsx("button", {
										style: s.btn,
										type: "button",
										onClick: () => setReveal((value) => !value),
										children: reveal ? "隐藏" : "显示",
									}),
									react_jsx_runtime.jsx("button", {
										style: s.btnPrimary,
										type: "button",
										disabled: busy,
										onClick: save,
										children: busy ? "保存中…" : "保存",
									}),
									react_jsx_runtime.jsx("button", {
										style: s.btn,
										type: "button",
										disabled: busy,
										onClick: clear,
										children: "清除",
									}),
								],
							}),
							react_jsx_runtime.jsx("p", {
								style: s.desc,
								children:
									"只写不读：保存后这个输入框不会回显，页面上也永远看不到已存的值。" +
									"需要 repo（私有仓库）以及 workflow（触发 CI / 传发布资产）权限。",
							}),
							message
								? react_jsx_runtime.jsx("p", { style: s.msg(message.ok), children: message.text })
								: null,
						],
					}),
					react_jsx_runtime.jsxs("p", {
						style: s.desc,
						children: [
							"也可以用文件：把 token 存成 ",
							react_jsx_runtime.jsx("code", { style: s.code, children: "/sdcard/Download/git-token.txt" }),
							" 一行即可，插件会自己读。",
						],
					}),
				],
			});
		}

		/** Client-side dependencies for the Settings registration. */
		const inject = ["slots"];

		/** Contribute the token tab to the Plugins settings section. */
		function apply(ctx) {
			ctx.slots.inject("settings.plugins.tab", () =>
				ctx.slots.register(
					{
						name: "settings.plugins.tab",
						id: "diksu-git-token",
						order: 30,
						label: () => "Diksu Git 凭据",
						inject: () => ({}),
					},
					DiksuGitTokenSettings,
				),
			);
		}

		exports.apply = apply;
		exports.inject = inject;
		return module.exports;
	},
});
