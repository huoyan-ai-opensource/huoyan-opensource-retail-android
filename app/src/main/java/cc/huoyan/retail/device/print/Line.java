package cc.huoyan.retail.device.print;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class Line implements Parcelable {

	private ArrayList<Unit> units = new ArrayList<Unit>();

	public Line() {
		this.units = new ArrayList<Unit>();
	}

	public ArrayList<Unit> getUnits() {
		return units;
	}

	public void setUnits(ArrayList<Unit> units) {
		this.units = units;
	}

	public Line addUnit(Unit unit) {
		units.add(unit);
		return this;
	}






	private Line(Parcel source) {
		source.readTypedList(this.units, Unit.CREATOR);
	}

	public static final Creator<Line> CREATOR = new Creator<Line>() {

		@Override
		public Line createFromParcel(Parcel source) {
			return new Line(source);
		}

		@Override
		public Line[] newArray(int size) {
			return new Line[size];
		}
	};

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeTypedList(units);
	}

}
