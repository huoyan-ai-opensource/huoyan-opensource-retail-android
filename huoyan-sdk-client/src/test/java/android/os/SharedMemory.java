package android.os;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 测试遮蔽类（参照 SDK 仓库 huoyan-sdk-android-universal 同名
 * 测试替身）：JVM 单测下的 android.os.SharedMemory 替身，遮蔽 mockable jar
 * 使 {@code AidlSdkBridge.newSharedMemoryRequest} 的建区/写入链路可纯 JUnit 验证。
 *
 * <p>与真实类（API 27+）的方法签名对齐，以堆内字节数组模拟共享内存区：
 * {@link #create(String, int)} 建区、{@link #mapReadWrite()}/{@link #mapReadOnly()}
 * 取映射视图、{@link #getSize()} 区容量；相等性按内容判定。真实实现的
 * parcel 往返转移 fd，测试侧按内容重建即等价。</p>
 */
public final class SharedMemory implements Parcelable {

    private final byte[] data;

    /** close 已触达标记（断言桥方法调用后释放载体）。 */
    private boolean closed;

    private SharedMemory(byte[] data) {
        this.data = data;
    }

    public static SharedMemory create(String name, int sizeInBytes) {
        if (sizeInBytes < 0) {
            throw new IllegalArgumentException("区容量不能为负：" + sizeInBytes);
        }
        return new SharedMemory(new byte[sizeInBytes]);
    }

    /** 区容量（字节）。 */
    public int getSize() {
        return data.length;
    }

    public ByteBuffer mapReadWrite() {
        return ByteBuffer.wrap(data);
    }

    public ByteBuffer mapReadOnly() {
        return ByteBuffer.wrap(data).asReadOnlyBuffer();
    }

    /** 堆缓冲区无需解除 native 映射，仅用于对齐平台签名。 */
    public static void unmap(ByteBuffer buffer) { }

    public void close() {
        // 测试替身无 native 资源可释放，仅记录触达供断言
        closed = true;
    }

    /** close 是否已被调用（真实实现释放在 native 层，不可观测）。 */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public int describeContents() {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(data.length);
        parcel.writeByteArray(data);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SharedMemory)) {
            return false;
        }
        return Arrays.equals(data, ((SharedMemory) other).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "SharedMemory(size=" + data.length + "B)";
    }

    public static final Creator<SharedMemory> CREATOR = new Creator<SharedMemory>() {
        @Override
        public SharedMemory createFromParcel(Parcel parcel) {
            int length = parcel.readInt();
            byte[] data = new byte[length];
            byte[] read = parcel.createByteArray();
            if (read == null || read.length != length) {
                throw new IllegalStateException("SharedMemory 载荷长度不一致");
            }
            System.arraycopy(read, 0, data, 0, length);
            return new SharedMemory(data);
        }

        @Override
        public SharedMemory[] newArray(int size) {
            return new SharedMemory[size];
        }
    };
}
