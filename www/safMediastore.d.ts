interface FileInfo {
  uri: string;
  name: string;
  lastModified: number;
  writable: boolean;
  type?: string;
  size?: number;
}

interface FileData {
  data: string | ArrayBuffer | ArrayBufferView | Blob;
  mimeType?: string | null;
}

interface SafMediastore {
  selectFolder(params?: { folder?: string | null; title?: string | null; writable?: boolean | null } | null): Promise<FileInfo>;

  selectFile(params?: { folder?: string | null; title?: string | null; writable?: boolean | null; mimeTypes?: string[] | null } | null): Promise<FileInfo>;

  openFolder(params: { uri: string; title?: string | null }): Promise<void>;

  openFile(params: { uri: string; title?: string | null }): Promise<void>;

  readFile(params: { uri: string }): Promise<Blob>;

  saveFile(params: FileData & { folder?: string | null; filename?: string | null }): Promise<FileInfo>;

  writeFile(params: FileData & { uri: string; path?: string | null }): Promise<FileInfo>;

  writeMedia(params: FileData & { path: string }): Promise<FileInfo>;

  overwriteFile(params: FileData & { uri: string }): Promise<FileInfo>;

  deleteFile(params: { uri: string }): Promise<number>;

  getInfo(params: { uri: string }): Promise<FileInfo>;

  getUri(params: { uri: string; path: string }): Promise<{ uri: string | null }>;
}

interface CordovaPlugins {
  safMediastore: SafMediastore;
}
