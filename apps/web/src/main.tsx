import React from "react";
import { createRoot } from "react-dom/client";
import "./app.css";
import Workspace from "./App";

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Workspace />
  </React.StrictMode>,
);
