package cc.huoyan.app.aidl;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;

import androidx.annotation.RequiresApi;

import java.util.Objects;

/**
 * 识别/检测/学习请求的图像载体：三载体恰选一——
 *
 * <ul>
 *   <li>{@code sharedMemory}：{@link SharedMemory}（API 27+，大图主路径），
 *       {@code size} 为区内有效字节数；</li>
 *   <li>{@code bytes}：内联字节（小图/降级，≤{@link #MAX_INLINE_BYTES}），
 *       {@code size=0} 表示全长；</li>
 *   <li>{@code shmKey}：共享内存区域键（legacy 形态，不携带几何信息）。</li>
 * </ul>
 *
 * <p>全参载体（sharedMemory/bytes）要求 {@code width}*{@code height}*3 ≤ 有效字节数、
 * {@code format}={@link #FORMAT_RGB888}；契约校验规则集中在 {@link #validate()}
 * （纯逻辑、返回 null=合法或中文 reason），由适配层在映射前调用——parcel 本身
 * 不拒绝非法组合，便于跨进程调用方得到明确错误回执而非 RemoteException。</p>
 *
 * <p>AIDL 通道仅支持 API 27+；发送方负责解除映射并关闭自己的 SharedMemory，
 * 接收方关闭收到的独立句柄。共享内存有效字节不超过 32 MiB。</p>
 */
public final class ImageRequestParcel implements Parcelable {

    /** 内联字节上限（AIDL 通道小图/降级路径边界；WS base64 通道另有 2MB 口径）。 */
    public static final int MAX_INLINE_BYTES = 1024 * 1024;
    /** SharedMemory 有效字节接收上限，与 SDK 一致。 */
    public static final int MAX_SHARED_MEMORY_BYTES = 32 * 1024 * 1024;

    /** 像素格式：RGB888 紧凑帧（HWC、已转正；当前唯一支持格式）。 */
    public static final int FORMAT_RGB888 = 1;

    /** 像素格式：JPEG 原图（uploadPhoto 输入，不解析几何）。 */
    public static final int FORMAT_JPEG = 2;

    /** 像素格式：PNG 原图（多格式，不解析几何，解码得出尺寸）。 */
    public static final int FORMAT_PNG = 3;

    private final String shmKey;

    /** SharedMemory 载体（API 27+ 大图主路径）；null=未使用。 */
    @RequiresApi(27)
    private final SharedMemory sharedMemory;

    private final byte[] bytes;

    /** 区内/数组内有效字节数；0=未指定（bytes 载体视为全长，sharedMemory 载体非法）。 */
    private final int size;

    private final int width;

    private final int height;

    /** 像素格式（{@link #FORMAT_RGB888}）；legacy shmKey 载体为 0。 */
    private final int format;

    /** Legacy 形态（原契约）：shmKey/bytes 二选一，不携带几何信息。 */
    public ImageRequestParcel(String shmKey, byte[] bytes) {
        this(shmKey, null, bytes, 0, 0, 0, 0);
    }

    /** 全参形态：sharedMemory/bytes 恰选一 + 几何与格式（校验见 {@link #validate()}）。 */
    @RequiresApi(27)
    public ImageRequestParcel(SharedMemory sharedMemory, byte[] bytes,
            int size, int width, int height, int format) {
        this(null, sharedMemory, bytes, size, width, height, format);
    }

    private ImageRequestParcel(String shmKey, SharedMemory sharedMemory, byte[] bytes,
            int size, int width, int height, int format) {
        this.shmKey = shmKey;
        this.sharedMemory = sharedMemory;
        this.bytes = bytes;
        this.size = size;
        this.width = width;
        this.height = height;
        this.format = format;
    }

    /** shmKey（legacy 共享内存区域键）；null 表示未使用该载体。 */
    public String getShmKey() {
        return shmKey;
    }

    /** SharedMemory 载体（仅 API 27+ 非 null）；null 表示未使用该载体。 */
    @RequiresApi(27)
    public SharedMemory getSharedMemory() {
        return sharedMemory;
    }

    /** 内联图像字节；null 表示未使用内联通道。 */
    public byte[] getBytes() {
        return bytes;
    }

    /** 有效字节数（0=未指定）。 */
    public int getSize() {
        return size;
    }

    /** 帧宽（像素；legacy shmKey 载体为 0）。 */
    public int getWidth() {
        return width;
    }

    /** 帧高（像素；legacy shmKey 载体为 0）。 */
    public int getHeight() {
        return height;
    }

    /** 像素格式（{@link #FORMAT_RGB888}；legacy shmKey 载体为 0）。 */
    public int getFormat() {
        return format;
    }

    /**
     * 契约校验（适配层映射前调用）：返回 null=合法，
     * 否则返回中文 reason（进入 code=ERR_ARGS 的错误回执）。
     *
     * <ul>
     *   <li>载体恰选一：shmKey / sharedMemory / bytes；</li>
     *   <li>shmKey：非空白，且不携带几何信息（size/width/height/format 全 0）；</li>
     *   <li>bytes（legacy 无几何形态，size/width/height/format 全 0）：非空数组
     *       且 ≤{@link #MAX_INLINE_BYTES}；</li>
     *   <li>sharedMemory（API 27+）：format=RGB888、width/height 为正、
     *       0 &lt; size ≤ 区容量，width*height*3 ≤ size；</li>
     *   <li>bytes：非空数组且 ≤{@link #MAX_INLINE_BYTES}、format=RGB888、
     *       width/height 为正、size ∈ [0, bytes.length]（0=全长），
     *       width*height*3 ≤ 有效字节数。</li>
     * </ul>
     */
    public String validate() {
        int present = (shmKey != null ? 1 : 0) + (sharedMemory != null ? 1 : 0) + (bytes != null ? 1 : 0);
        if (present != 1) {
            return "图像载体必须为 shmKey/sharedMemory/bytes 恰选一（当前：" + present + " 个非空载体）";
        }
        if (shmKey != null) {
            if (shmKey.trim().isEmpty()) {
                return "shmKey 不能为空白";
            }
            if (size != 0 || width != 0 || height != 0 || format != 0) {
                return "shmKey 载体不携带几何信息（size/width/height/format 必须全 0）";
            }
            return null;
        }
        if (size == 0 && width == 0 && height == 0 && format == 0) {
            // legacy 无几何形态（原契约，仅 bytes 载体）：只查内联边界
            if (sharedMemory != null) {
                return "sharedMemory 载体必须携带几何信息（size/width/height/format）";
            }
            if (bytes.length == 0) {
                return "bytes 不能为空数组";
            }
            if (bytes.length > MAX_INLINE_BYTES) {
                return "bytes 超出内联上限（" + bytes.length + " > " + MAX_INLINE_BYTES + "）";
            }
            return null;
        }
        if (format != FORMAT_RGB888 && format != FORMAT_JPEG && format != FORMAT_PNG) {
            return "像素格式必须是 RGB888/JPEG/PNG 之一（当前：" + format + "）";
        }
        boolean needsGeometry = format == FORMAT_RGB888;
        if (needsGeometry && (width <= 0 || height <= 0)) {
            return "RGB888 载体必须携带 width/height（当前：width=" + width + ", height=" + height + "）";
        }
        long effective;
        if (sharedMemory != null) {
            int capacity = sharedMemory.getSize();
            if (size <= 0) {
                return "sharedMemory 载体必须携带有效字节数 size（>0）";
            }
            if (size > capacity) {
                return "size 超出共享内存容量（" + size + " > " + capacity + "）";
            }
            if (size > MAX_SHARED_MEMORY_BYTES) return "sharedMemory 有效字节数超过 32 MiB";
            effective = size;
        } else {
            if (bytes.length == 0) {
                return "bytes 不能为空数组";
            }
            if (bytes.length > MAX_INLINE_BYTES) {
                return "bytes 超出内联上限（" + bytes.length + " > " + MAX_INLINE_BYTES + "）";
            }
            if (size < 0 || size > bytes.length) {
                return "size 必须在 0..bytes.length（当前：" + size + "，bytes.length=" + bytes.length + "）";
            }
            effective = size == 0 ? bytes.length : size;
        }
        if (needsGeometry) {
            long required = (long) width * height * 3;
            if (required > effective) {
                return "width*height*3 超出有效字节数（" + required + " > " + effective + "）";
            }
        }
        return null;
    }

    private ImageRequestParcel(Parcel parcel) {
        shmKey = parcel.readString();
        sharedMemory = (SharedMemory) parcel.readValue(ImageRequestParcel.class.getClassLoader());
        bytes = parcel.createByteArray();
        size = parcel.readInt();
        width = parcel.readInt();
        height = parcel.readInt();
        format = parcel.readInt();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(shmKey);
        parcel.writeValue(sharedMemory);
        parcel.writeByteArray(bytes);
        parcel.writeInt(size);
        parcel.writeInt(width);
        parcel.writeInt(height);
        parcel.writeInt(format);
    }

    @Override
    public int describeContents() {
        return sharedMemory != null ? sharedMemory.describeContents() : 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ImageRequestParcel)) {
            return false;
        }
        ImageRequestParcel that = (ImageRequestParcel) other;
        return size == that.size
                && width == that.width
                && height == that.height
                && format == that.format
                && Objects.equals(shmKey, that.shmKey)
                && Objects.equals(sharedMemory, that.sharedMemory)
                && java.util.Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(shmKey);
        result = 31 * result + Objects.hashCode(sharedMemory);
        result = 31 * result + java.util.Arrays.hashCode(bytes);
        result = 31 * result + size;
        result = 31 * result + width;
        result = 31 * result + height;
        return 31 * result + format;
    }

    @Override
    public String toString() {
        return "ImageRequestParcel(shmKey=" + shmKey + ", sharedMemory="
                + (sharedMemory == null ? "null" : sharedMemory.getSize() + "B") + ", bytes="
                + (bytes == null ? "null" : bytes.length + "B")
                + ", size=" + size + ", width=" + width + ", height=" + height + ", format=" + format + ")";
    }

    public static final Creator<ImageRequestParcel> CREATOR = new Creator<ImageRequestParcel>() {
        @Override
        public ImageRequestParcel createFromParcel(Parcel parcel) {
            return new ImageRequestParcel(parcel);
        }

        @Override
        public ImageRequestParcel[] newArray(int size) {
            return new ImageRequestParcel[size];
        }
    };
}
