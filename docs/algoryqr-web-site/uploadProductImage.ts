import { api } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";

export type ProductImageUploadResponse = {
  imageUrl: string;
  objectKey: string;
};

const UPLOAD_TIMEOUT_MS = 60_000;

export async function uploadProductImage(
  menuId: number,
  file: File,
): Promise<ProductImageUploadResponse> {
  const form = new FormData();
  form.append("file", file);

  try {
    const response = await api.post<ProductImageUploadResponse>(
      `/menu/${menuId}/products/images`,
      form,
      {
        headers: { "Content-Type": undefined },
        timeout: UPLOAD_TIMEOUT_MS,
      },
    );
    return response.data;
  } catch (error) {
    if (error instanceof ApiError) {
      throw new Error(error.message || "Görsel yüklenemedi");
    }
    throw error;
  }
}

export async function deleteProductImage(
  menuId: number,
  params: { objectKey?: string; imageUrl?: string },
): Promise<void> {
  try {
    await api.delete(`/menu/${menuId}/products/images`, {
      params: {
        ...(params.objectKey ? { objectKey: params.objectKey } : {}),
        ...(params.imageUrl ? { imageUrl: params.imageUrl } : {}),
      },
    });
  } catch (error) {
    if (error instanceof ApiError) {
      throw new Error(error.message || "Görsel silinemedi");
    }
    throw error;
  }
}
