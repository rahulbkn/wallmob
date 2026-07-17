import multer from "multer";
/** In-memory storage — files are handed straight to StorageProvider as
 *  Buffers, never touching local disk (except inside LocalStorageProvider
 *  itself, which is expected to write to disk). */
export const uploadMiddleware = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 200 * 1024 * 1024 }
}).fields([
    { name: "video", maxCount: 1 },
    { name: "thumbnail", maxCount: 1 }
]);
