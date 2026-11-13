
import { createHash } from "node:crypto";
export const ALLOWED_FOLDERS = new Set(["youthverse/covers", "youthverse/chapters"]);
export function createCloudinarySignature({ folder, publicId, timestamp, apiSecret }) {
  if (!ALLOWED_FOLDERS.has(folder)) throw new Error("Unsupported upload folder");
  if (!/^[A-Za-z0-9_-]{8,80}$/.test(publicId)) throw new Error("Invalid public ID");
  if (!Number.isSafeInteger(timestamp) || timestamp <= 0) throw new Error("Invalid timestamp");
  if (typeof apiSecret !== "string" || apiSecret.length < 8) throw new Error("Cloudinary secret is not configured");
  const canonical = [`folder=${folder}`, `public_id=${publicId}`, `timestamp=${timestamp}`].sort().join("&");
  return createHash("sha1").update(canonical + apiSecret, "utf8").digest("hex");
}
