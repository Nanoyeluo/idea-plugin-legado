import axios from "axios";

// @ts-ignore
const vscode = typeof acquireVsCodeApi === "function" ? acquireVsCodeApi() : undefined;

const isVscode = () => !!vscode;

const getLegadoWebServeUrl = () => {
  let legadoWebServeUrl = localStorage.getItem("legadoWebServeUrl");
  return legadoWebServeUrl || import.meta.env.VITE_API || location.origin;
};

const setLegadoWebServeUrl = (url) => {
  localStorage.setItem("legadoWebServeUrl", url);
  if (vscode) {
    vscode.postMessage({
      command: "setConfiguration",
      key: "legado-vscode.webServeUrl",
      value: url
    });
  }
};

// 通过插件代理 /api/* 测试当前保存的 Legado 后端是否可达
const checkLegadoWebServeUrl = () => {
  return axios
    .create({
      baseURL: "/api",
      timeout: 3000
    })
    .get("/getBookshelf");
};

const reload = () => {
  if (vscode) {
    vscode.postMessage({
      command: "reload"
    });
  } else {
    location.reload();
  }
};

export default {
  isVscode,
  getLegadoWebServeUrl,
  setLegadoWebServeUrl,
  checkLegadoWebServeUrl,
  reload
};
