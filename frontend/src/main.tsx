import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { APP_BASE } from "./shared/appPath";
import App from "./app/App";
import "./app/global.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter basename={APP_BASE || "/"}>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
