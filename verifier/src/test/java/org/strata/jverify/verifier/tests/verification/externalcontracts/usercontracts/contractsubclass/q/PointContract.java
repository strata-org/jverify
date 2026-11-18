package q;

import org.strata.jverify.Contract;

@Contract(java.awt.Point.class)
class PointContract {
    public int x;
    public int y;

    public PointContract(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
