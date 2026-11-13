
import test from "node:test";
import assert from "node:assert/strict";
import { createCloudinarySignature } from "./cloudinary-signature.mjs";
test("creates a SHA-1 Cloudinary signature", () => {
  const result = createCloudinarySignature({folder:"youthverse/covers",publicId:"yv_1234567890abcdef",timestamp:1730000000,apiSecret:"test-secret-value"});
  assert.equal(result, "c7718553f07ad1245ef68b27e50bf274406956f8");
});
test("rejects non-allowlisted folders", () => assert.throws(() => createCloudinarySignature({folder:"youthverse/arbitrary",publicId:"yv_1234567890abcdef",timestamp:1730000000,apiSecret:"test-secret-value"}), /Unsupported upload folder/));
test("rejects unsafe public IDs and timestamps", () => {
  assert.throws(() => createCloudinarySignature({folder:"youthverse/covers",publicId:"../unsafe",timestamp:1730000000,apiSecret:"test-secret-value"}), /Invalid public ID/);
  assert.throws(() => createCloudinarySignature({folder:"youthverse/covers",publicId:"yv_1234567890abcdef",timestamp:0,apiSecret:"test-secret-value"}), /Invalid timestamp/);
});
