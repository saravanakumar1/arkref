package analysis;

import parsestuff.AnalysisUtilities;
import parsestuff.TregexPatternFactory;
import data.FirstNames;
import data.Mention;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

public class Types {
	
	public static enum Gender {
		Male, Female, Unknown;
		public String toString() {
			switch(this) {
			case Male: return "Mal";
			case Female: return "Fem";
			case Unknown: return "";
			}
			return "";
		}
	}
	public static enum Personhood {
		Person, NotPerson, MaybePerson;
		public String toString() {
			switch(this) {
			case Person: return "Per";
			case NotPerson: return "NPer";
			case MaybePerson: return "MaybePer";
			}
			return "";
		}
	}
	public static enum Number {
		Singular, Plural;
		public String toString() {
			switch(this) {
			case Singular: return "Sg";
			case Plural: return "Pl";
			}
			return "";
		}
	}
	public static enum Perspective {
		First, Second, Third;
		public String toString() {
			switch(this) {
			case First: return "1";
			case Second: return "2";
			case Third: return "3";
			default: return "";
			}
		}
	}
	
	public static <T> boolean relaxedEquals(T x, T y) {
		if (x==null || y==null)
			return true;
		return x==y;
	}
	
	
	public static boolean sexistGenderEquals(Gender x, Gender y) {
		// see testDefaultMale()
		// unknown gender defaults to male
		// unknown gender cannot match female
		if (x==null && y==null) return true;
		if (x==null && y==Gender.Male) return true;
		if (y==null && x==Gender.Male) return true;
		return x==y;
	}
	
	
	public static boolean personhoodEquals(Personhood x, Personhood y) {
		// see testEntityTypeMatching(), testThey()
		if ((x==null || x==Personhood.NotPerson || x==Personhood.MaybePerson) 
				&& (y==null || y==Personhood.NotPerson || y==Personhood.MaybePerson))
		{
			return true;
		}
		if ((x==Personhood.Person || x==Personhood.MaybePerson) 
				&& (y==Personhood.Person || y==Personhood.MaybePerson))
		{
			return true;
		}
		return x==y;
	}


	public static boolean checkPronominalMatch(Mention mention, Mention cand) {
		assert isPronominal(mention);
		String pronoun = pronoun(mention);
		if (!isPronominal(cand) && perspective(pronoun) == Perspective.First) {
			// testFirstPerson
			return false;
		}
		if (SyntacticPaths.aIsDominatedByB(mention, cand)){ // I-within-I constraint 
			return false;
		}
		// using lax test on personhood because i don't know how to get it for most common nouns
		// number is easiest to get
		// gender is gray area
		return
			//relaxedEquals(personhood(pronoun), personhood(cand)) &&
			personhoodEquals(personhood(pronoun), personhood(cand)) &&
			sexistGenderEquals(gender(mention), gender(cand)) &&
			/* DISABLED gender(mention) == gender(cand) && */
			number(mention) == number(cand);
	}
	public static boolean isPronominal(Mention m) {
		TregexMatcher matcher = TregexPatternFactory.getPattern("NP <<# /^PRP/ !> NP").matcher(m.getNode());
		return matcher.find();
	}
	
	public static String pronoun(Mention m) {
		TregexPattern pat = TregexPatternFactory.getPattern("NP=np <<# /^PRP/=pronoun !> NP");
		TregexMatcher matcher = pat.matcher(m.getNode());
		if (matcher.find()) {
			Tree PRP = matcher.getNode("pronoun");
			return pronoun(PRP);
		} else {
			return null;
		}
	}
	
	public static String pronoun(Tree PRP) {
		Tree c = PRP.getChild(0);
		assert c.isLeaf();
		String p = c.label().toString().toLowerCase();
		return p;
	}
	
	public static Gender gender(Mention m) {
		if (isPronominal(m)) {
			String p = pronoun(m);
			if (p.matches("^(he|him|his)$")) {
				return Gender.Male;
			} else if (p.matches("^(she|her|hers)$")) {
				return Gender.Female;
			} else if (p.matches("^(it|its)$")) {
				return null;
//				return Gender.Neuter;
			} else {
				return null;   // no decision
			}
		}
		
		//if its something other than PERSON or other (e.g., LOCATION)
		//then return null because its obviously not male or female.
		String neType = m.neType();
		if(!neType.equals("PERSON") && !neType.equals("O")){
			return null;
		}
		
		//Go through all the NNP tokens in the noun phrase and see if any of them
		//are person names.  If so, return the gender of that name.
		//Note: this will fail for ambiguous month/person names like "April"
		for(Tree leaf : m.getNode().getLeaves()){
			if(!leaf.parent(m.getNode()).label().value().equals("NNP")){
				continue;
			}
			String genderS = FirstNames.getInstance().getGenderString(leaf.value());
			if(genderS.equals("Mal")){
				return Gender.Male;
			}else if(genderS.equals("Fem")){
				return Gender.Female;
			}
		}
		return null;
	}
	
	public static Personhood personhood(Mention m) {
		if (isPronominal(m)) {
			String p = pronoun(m);
			return personhood(p);
		}
		String t = m.neType();
		if (t.equals("PERSON") || NounTypes.getInstance().getType(m.getHeadWord()).equals("person")) return Personhood.Person;
		if (t.equals("O")) return null;
		return Personhood.NotPerson;
	}
	public static Personhood personhood(String pronoun) {
		if (pronoun.matches("^(he|him|his|she|her|hers|we|us|our|ours|i|my|mine|you|yours)$")) {
			return Personhood.Person;
		} else if (pronoun.matches("^(it|its)$")) {
			return Personhood.NotPerson;
		}else if (pronoun.matches("^(they|their|theirs|them|these|those)$")) {
			return Personhood.MaybePerson;
		}
		return null;
	}
	
	/** what the heck is the real name for this? **/
	public static Perspective perspective(String pronoun) {
		if (pronoun.matches("^(i|me||my|mine|we|our|ours)$")) {
			return Perspective.First;
		} else if (pronoun.matches("^(you|yours|y'all|y'alls|yinz)$")) {
			return Perspective.Second;
		} else {
			return Perspective.Third;
		}
	}
	
	public static Number number(Mention m) {
		if (isPronominal(m)) {
			String p = pronoun(m);
			if (p.matches("^(they|them|these|those|we|us|their|ours|our|theirs)$")) {
				return Number.Plural;
			} else {  //if (p.matches("^(it|its|that|this|he|him|his|she|her)$")) {
				return Number.Singular;
			}
		} else {
			HeadFinder hf = AnalysisUtilities.getInstance().getHeadFinder();
			Tree head = m.getNode().headPreTerminal(hf);
			String tag = head.label().toString();
			// http://bulba.sdsu.edu/jeanette/thesis/PennTags.html
			if (tag.matches("^NNP?S$")) return Number.Plural;
			if (tag.matches("^NNP?$"))  return Number.Singular;
			// TODO mass nouns?
		}
		return null;
	}

}