/**
 * 
 */
package com.alphasystem.morphologicalanalysis.util;

import java.util.Comparator;

import com.alphasystem.morphologicalanalysis.model.Chapter;

/**
 * @author sali
 * 
 */
public class ChapterComparator implements Comparator<Chapter> {

	@Override
	public int compare(Chapter o1, Chapter o2) {
		int result = 0;
		if (o1 == null && o2 == null) {
			result = 0;
		} else if (o1 == null) {
			result = -1;
		} else if (o2 == null) {
			result = 1;
		} else {
			result = o1.getChapterNumber().compareTo(o2.getChapterNumber());
		}
		return result;
	}
}
