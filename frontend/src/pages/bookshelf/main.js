import { createApp } from "vue";
import App from "@/App.vue";
import bookRouter from "@/router";
import store from "@/store";
import "@/assets/darcula-theme.css";

createApp(App).use(store).use(bookRouter).mount("#app");

import("./config");
