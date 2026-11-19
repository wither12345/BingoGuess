package dev.guessbingo.lex;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次猜测与谜底比对后的结果：名称列（含逐字提示）加上
 * 加入版本、类型、标签、子标签、字数、维度六列。
 */
public final class GuessRow {
	/** 判色编号，沿用「猜词」数据包的约定。 */
	public static final int GREEN = 1, RED = 2, AMBER = 3, YELLOW = 5, ORANGE = 6, CYAN = 7, BLUE = 8;
	/** 名称末尾的类型括号：不参与逐字比较 */
	public static final int PLAIN = 0;

	public static final String[] COLUMNS = {"名称", "加入版本", "类型", "标签", "子标签", "字数", "维度"};

	public static final class Cell {
		public String x;
		/** 判色 */
		public int k;
		/** 1 = 答案更晚 / 更多，-1 = 更早 / 更少，0 = 无方向 */
		public int a;
		/** 悬停说明 */
		public String h;

        public Cell(String text, int kind, int arrow, String hover) {
			this.x = text;
			this.k = kind;
			this.a = arrow;
			this.h = hover;
		}

		public String display() {
			return a > 0 ? x + "↑" : a < 0 ? x + "↓" : x;
		}
	}

	public static final class Letter {
		public String c;
		public int k;

		public Letter(String c, int k) {
			this.c = c;
			this.k = k;
		}
	}

    public static class Progress{
        public int progress;
        public boolean ok;

        public void reset(){
            progress = 0;
            ok = false;
        }
    }

	/** 被猜的词条名 */
	public String name;
	/** 名称整体的判色与说明 */
	public int nk;
	public String nh;
	/** 逐字提示 */
	public List<Letter> letters = new ArrayList<>();
	/** 其余六列 */
	public List<Cell> cells = new ArrayList<>();
	/** 是否猜中 */
	public boolean ok;
	/** 提交者与所属队伍（由对局逻辑填写） */
	public String by = "";
	public String team = "";

    public int getProgress(Progress progress){
        int ret = 0;
        if(this.ok && !progress.ok) {
            ret ++ ;
            progress.ok = true;
        }

        for(int i = 0; i < 6; i ++){
            Cell now = this.cells.get(i);
            if(((progress.progress & (1 << i)) == 0) && now.k == GREEN) {
                progress.progress |= (1 << i);
                ret++;
            }
        }
        return ret;
    }
}
