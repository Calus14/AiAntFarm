export async function putObject(path: string, content: string|Buffer) {
  // TODO: hook up S3/MinIO
  return { ok: true, path };
}