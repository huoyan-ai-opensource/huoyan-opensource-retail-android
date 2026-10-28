package cc.huoyan.retail.device.print;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.io.ByteArrayOutputStream;
import java.util.Hashtable;

public class Unit implements Parcelable {

	public static enum EALIGN {
		LEFT, CENTER, RIGHT
	}

	/**
	 * 打印内容， String类型
	 */
	private String content = "";
	/**
	 * 打印的图片
	 */
	private Bitmap bitmap;
	/**
	 * 图片类型
	 */
	private BarcodeFormat type = BarcodeFormat.QR_CODE;
	/**
	 * 字体大小
	 */
	private int fontSize = 15;
	private int rowSpacing = 1;
	/**
	 * 对齐方式
	 */
	private EALIGN align = EALIGN.LEFT;;
	/**
	 * 是否粗体
	 */
	private boolean isBold = false;
	/**
	 * 权重
	 */
	private float weight = 1;
	/**
	 * 是否换行
	 */
	private boolean newline = true;

	/**
	 * 构造方法
	 */

	public Unit() {

	}

	/**
	 * 构造方法
	 * 
	 * @param content
	 * @param fontSize
	 * @param align
	 * @param isBold
	 */
	public Unit(String content, int fontSize, EALIGN align, boolean isBold) {
		this.content = content;
		this.fontSize = fontSize;
		this.align = align;
		this.isBold = isBold;
	}

	public Unit(String content, int fontSize, EALIGN align, boolean isBold, float weight) {
		this.content = content;
		this.fontSize = fontSize;
		this.align = align;
		this.isBold = isBold;
		this.weight = weight;
	}

	/**
	 * 
	 * @param content
	 * @param fontSize
	 * @param align
	 */
	public Unit(String content, int fontSize, EALIGN align) {
		this.content = content;
		this.fontSize = fontSize;
		this.align = align;
		this.isBold = false;
	}

	public Unit(String content, int fontSize, EALIGN align, int rowSpacing) {
		this.content = content;
		this.fontSize = fontSize;
		this.align = align;
		this.isBold = false;
		this.rowSpacing = rowSpacing;
	}

	public Unit(String content, int fontSize, EALIGN align, float weight) {
		this.content = content;
		this.fontSize = fontSize;
		this.align = align;
		this.isBold = false;
		this.weight = weight;
	}

	/**
	 * 构造方法, 左对齐
	 * 
	 * @param content
	 * @param fontSize
	 * @param isBold
	 */
	public Unit(String content, int fontSize, boolean isBold) {
		this.content = content;
		this.fontSize = fontSize;
		this.isBold = isBold;
		this.align = EALIGN.LEFT;
	}

	public Unit(String content, int fontSize, boolean isBold, float weight) {
		this.content = content;
		this.fontSize = fontSize;
		this.isBold = isBold;
		this.align = EALIGN.LEFT;
		this.weight = weight;
	}

	/**
	 * 构造方法, 非粗体，左对齐
	 * 
	 * @param content
	 * @param fontSize
	 */
	public Unit(String content, int fontSize) {
		this.content = content;
		this.fontSize = fontSize;
		this.align = EALIGN.LEFT;
		this.isBold = false;
	}

	public Unit(String content, int fontSize, float weight) {
		this.content = content;
		this.fontSize = fontSize;
		this.align = EALIGN.LEFT;
		this.isBold = false;
		this.weight = weight;
	}

	/**
	 * 构造方法， 成员为bitmap
	 * 
	 * @param bitmap
	 */
	public Unit(Bitmap bitmap) {
		this.bitmap = bitmap;
		this.align = EALIGN.LEFT;
	}

	/**
	 * 构造方法， 成员为bitmap
	 * 
	 * @param bitmap
	 */
	public Unit(Bitmap bitmap, EALIGN align) {
		this.bitmap = bitmap;
		this.align = align;
	}

	/*******************************
	 * 添加打印 条码/二维码 的构造方法 ,默认条码/二维码的宽高为 480*160/320*320
	 *************/
	/**
	 * 构造方法，codeContent,type,align
	 * 
	 * @param codeContent
	 * @param type
	 * @param align
	 * @throws WriterException
	 */
	public Unit(String codeContent, BarcodeFormat type, EALIGN align) throws WriterException {
		if (!TextUtils.isEmpty(codeContent)) {
			if (type.equals(BarcodeFormat.CODE_128) || type.equals(BarcodeFormat.CODE_39)
					|| type.equals(BarcodeFormat.CODE_93)) {
				this.bitmap = CreateCode(codeContent, type, 480, 160);
			} else {
				this.bitmap = CreateCode(codeContent, type, 320, 320);
			}

		}
		this.align = align;
	}

	/**
	 * 构造方法，codeContent,type,codeHeight,codeWidth,align
	 * 
	 * @param codeContent
	 * @param type
	 * @param codeHeight
	 * @param codeWidth
	 * @param align
	 * @throws WriterException
	 */

	public Unit(String codeContent, BarcodeFormat type, int codeHeight, int codeWidth, EALIGN align)
			throws WriterException {
		if (!TextUtils.isEmpty(codeContent)) {
			this.bitmap = CreateCode(codeContent, type, codeHeight, codeWidth);
		}
		this.align = align;
	}

	public int getRowSpacing() {
		return rowSpacing;
	}

	public void setRowSpacing(int rowSpacing) {
		this.rowSpacing = rowSpacing;
	}

	/*********************************************************************/

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public void setBitmap(Bitmap bitmap) {
		this.bitmap = bitmap;
	}

	public int getFontSize() {
		return fontSize;
	}

	public void setFontSize(int fontSize) {
		this.fontSize = fontSize;
	}

	public EALIGN getAlign() {
		return align;
	}

	public void setAlign(EALIGN align) {
		this.align = align;
	}

	public boolean isBold() {
		return isBold;
	}

	public void setBold(boolean isBold) {
		this.isBold = isBold;
	}

	public float getWeight() {
		return weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	private Unit(Parcel source) {

		this.content = source.readString();
		int length = source.readInt();
		byte[] data = new byte[length];
		source.readByteArray(data);
		this.bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
		this.fontSize = source.readInt();
		this.align = EALIGN.valueOf(source.readString());

		String temp = source.readString();
		if (temp.equals("1")) {
			this.isBold = true;
		} else {
			this.isBold = false;
		}

		this.weight = source.readFloat();
	}

	public static final Creator<Unit> CREATOR = new Creator<Unit>() {

		@Override
		public Unit createFromParcel(Parcel source) {
			return new Unit(source);
		}

		@Override
		public Unit[] newArray(int size) {
			return new Unit[size];
		}
	};

	@Override
	public void writeToParcel(Parcel dest, int flags) {

		dest.writeString(content);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (this.bitmap != null) {
			this.bitmap.compress(Bitmap.CompressFormat.PNG, 0, baos);
			byte[] data = baos.toByteArray();
			dest.writeInt(data.length);
			dest.writeByteArray(data);
		} else {
			dest.writeInt(0);
			dest.writeByteArray(new byte[0]);
		}

		dest.writeInt(fontSize);

		dest.writeString(align.name());

		if (isBold) {
			dest.writeString("1");
		} else {
			dest.writeString("0");
		}

		dest.writeFloat(weight);
	}

	public static Bitmap CreateCode(String str, BarcodeFormat type, int bmpWidth, int bmpHeight)
			throws WriterException {
		Hashtable<EncodeHintType, String> mHashtable = new Hashtable<EncodeHintType, String>();
		mHashtable.put(EncodeHintType.CHARACTER_SET, "UTF-8");
		// 生成二维矩阵,编码时要指定大小,不要生成了图片以后再进行缩放,以防模糊导致识别失败
		BitMatrix matrix = new MultiFormatWriter().encode(str, type, bmpWidth, bmpHeight, mHashtable);
		int width = matrix.getWidth();
		int height = matrix.getHeight();
		// 二维矩阵转为一维像素数组（一直横着排）
		int[] pixels = new int[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (matrix.get(x, y)) {
					pixels[y * width + x] = 0xff000000;
				} else {
					pixels[y * width + x] = 0xffffffff;
				}
			}
		}

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		// 通过像素数组生成bitmap,具体参考api
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		return createCenterBitmap(matrix, bitmap);
	}

	/**
	 * 判断是否有白边，如果有，就把二维码给截取出来
	 */
	private static Bitmap createCenterBitmap(BitMatrix bitMatrix, Bitmap bitmap) {
		try {
			int[] topLeftOnBit = bitMatrix.getTopLeftOnBit();
			int[] bottomRightOnBit = bitMatrix.getBottomRightOnBit();
			int left = topLeftOnBit[0];
			int top =  topLeftOnBit[1];
			int right = bottomRightOnBit[0];
			int bottom = bottomRightOnBit[1];
			if( left > 0 && top > 0 && left < right && top < bottom){
				int width = right - left;
				int height = bottom - top;
				return Bitmap.createBitmap(bitmap, left, top, width, height);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bitmap;
	}
}
