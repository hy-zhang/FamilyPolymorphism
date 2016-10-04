import java.util.ArrayList;
import lombok.Obj;


public class Test3 {

	public static void main(String[] args) {
		
		for (int i = 1; i <= 50; i++) {
			System.out.println("------ i = " + i + " ------\n" + PrettyPrinter.print(i) + "\n-------------------\n\n");	
		}
		
	}

}

@Obj
interface PrettyPrinter {
	class Pair {
		int x; Document y;
		Pair(int x, Document y) { this.x = x; this.y = y; }
	}
	static Doc be(int w, int k, ArrayList<Pair> z) {
		if (z.isEmpty()) return Nil.of();
		Pair p = z.get(0);
		ArrayList<Pair> z2 = new ArrayList<Pair> ();
		z2.addAll(z); z2.remove(0);
		return p.y.beaux(w, k, p.x, z2);
	}
	interface Document {
		Doc beaux(int w, int k, int i, ArrayList<Pair> z);
		Document flatten();
		default Document group() {
			return DUnion.of(this.flatten(), this);
		}
		default Doc best(int w, int k) {
			ArrayList<Pair> z = new ArrayList<Pair>();
			z.add(new Pair(0, this));
			return be(w, k, z);
		}
		default String pretty(int w) {
			return this.best(w, 0).layout();
		}
	}
	interface DNil extends Document {
		default Document flatten() {
			return DNil.of();
		}
		default Doc beaux(int w, int k, int i, ArrayList<Pair> z) {
			return be(w, k, z);
		}
	}
	interface DConcat extends Document { Document d1(); Document d2();
		default Document flatten() {
			return DConcat.of(d1().flatten(), d2().flatten());
		}
		default Doc beaux(int w, int k, int i, ArrayList<Pair> z) {
			ArrayList<Pair> z2 = new ArrayList<Pair>();
			z2.add(new Pair(i, d1()));
			z2.add(new Pair(i, d2()));
			z2.addAll(z);
			return be(w, k, z2);
		}
	}
	interface DNest extends Document { int i(); Document d(); 
		default Document flatten() {
			return DNest.of(d().flatten(), i());
		}
		default Doc beaux(int w, int k, int i, ArrayList<Pair> z) {
			ArrayList<Pair> z2 = new ArrayList<Pair>();
			z2.add(new Pair(i + i(), d()));
			z2.addAll(z);
			return be(w, k, z2);
		}
	}
	interface DText extends Document { String s(); 
		default Document flatten() {
			return DText.of(s());
		}
		default Doc beaux(int w, int k, int i, ArrayList<Pair> z) {
			return Text.of(be(w, k + s().length(), z), s());
		}
	}
	interface DLine extends Document {
		default Document flatten() {
			return DText.of(" ");
		}
		default Doc beaux(int w, int k, int i, ArrayList<Pair> z) {
			return Line.of(be(w, i, z), i);
		}
	}
	interface DUnion extends Document { Document d1(); Document d2(); 
		default Document flatten() {
			return d1().flatten();
		}
		default Doc beaux(int w, int k, int i, ArrayList<Pair> z) {
			ArrayList<Pair> z2 = new ArrayList<Pair>();
			z2.add(new Pair(i, d1()));
			z2.addAll(z);
			ArrayList<Pair> z3 = new ArrayList<Pair>();
			z3.add(new Pair(i, d2()));
			z3.addAll(z);
			Doc x = be(w, k, z2);
			Doc y = be(w, k, z3);
			if (x.fits(w - k)) return x;
			else return y;
		}
	}
	
	interface Doc {
		boolean fits(int w);
		String layout();
	}
	interface Nil extends Doc {
		default boolean fits(int w) {
			if (w < 0) return false;
			return true;
		}
		default String layout() {
			return "";
		}
	}
	interface Text extends Doc {
		String s(); Doc d();
		default boolean fits(int w) {
			if (w < 0) return false;
			return d().fits(w - s().length());
		}
		default String layout() {
			return s() + d().layout();
		}
	}
	interface Line extends Doc {
		int i(); Doc d();
		default boolean fits(int w) {
			if (w < 0) return false;
			return true;
		}
		default String layout() {
			String indent = new String(new char[i()]).replace("\0", " ");
			return "\n" + indent + d().layout();
		}
	}
	
	// Tree example.
	interface Tree {
		String s(); ArrayList<Tree> subTrees();
		default Document showTree() {
			Document showBracket = null;
			if (subTrees().isEmpty()) {
				showBracket = new DNil(){};
			} else {
				Document showTrees = null;
				// left/right-associative?
				showTrees = subTrees().get(0).showTree();
				for (int i = 1; i < subTrees().size(); i++)
					showTrees = DConcat.of(DConcat.of(DConcat.of(showTrees, DText.of(",")), DLine.of()), subTrees().get(i).showTree());
				showBracket = DConcat.of(DConcat.of(DText.of("["), DNest.of(showTrees, 1)), DText.of("]"));
			}
			return DConcat.of(DText.of(s()), DNest.of(showBracket, s().length())).group();
		}
	}
	
	static Tree buildTree() {
		ArrayList<Tree> ts = new ArrayList<Tree>();
		ArrayList<Tree> tts = new ArrayList<Tree>();
		ArrayList<Tree> ttts = new ArrayList<Tree>();
		tts.add(Tree.of("ccc", new ArrayList<Tree>()));
		tts.add(Tree.of("dd", new ArrayList<Tree>()));
		ttts.add(Tree.of("gg", new ArrayList<Tree>()));
		ttts.add(Tree.of("hhh", new ArrayList<Tree>()));
		ttts.add(Tree.of("ii", new ArrayList<Tree>()));
		ts.add(Tree.of("bbbbb", tts));
		ts.add(Tree.of("eee", new ArrayList<Tree>()));
		ts.add(Tree.of("ffff", ttts));
		return Tree.of("aaa", ts);
	}
	
	static String print(int w) {
		return buildTree().showTree().pretty(w);
	}
}
