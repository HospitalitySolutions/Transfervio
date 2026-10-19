import SwaggerParser from "@apidevtools/swagger-parser";
import { fileURLToPath } from "node:url";
const spec = await SwaggerParser.validate(
  fileURLToPath(new URL("../../docs/api/openapi.yaml", import.meta.url)),
);
if (spec.openapi !== "3.1.0") throw Error("OpenAPI 3.1 is required");
for (const [path, item] of Object.entries(spec.paths))
  for (const [method, operation] of Object.entries(item))
    if (
      ["get", "post", "put", "patch", "delete"].includes(method) &&
      path.startsWith("/v1/") &&
      !operation.security?.some((s) => "partnerBearer" in s)
    )
      throw Error("Missing partner authentication contract");
console.log(
  `OpenAPI 3.1 validation passed: ${Object.keys(spec.paths).length} paths.`,
);
