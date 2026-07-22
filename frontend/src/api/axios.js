import axios from "axios";

const SECOND = 1000;

const ajax = axios.create({
  // 在 IDEA 插件中通过 LegadoResourceHandler 的 /api/* 代理访问 Legado 后端，避免 JCEF 跨域限制
  baseURL: "/api",
  timeout: 120 * SECOND,
});

export default ajax;
