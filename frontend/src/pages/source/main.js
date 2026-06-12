import { createApp } from "vue";
import App from "@/App.vue";
import sourceRouter from "@/router";
import store from "@/store";
import "@/assets/darcula-theme.css";

createApp(App).use(store).use(sourceRouter).mount("#app");
