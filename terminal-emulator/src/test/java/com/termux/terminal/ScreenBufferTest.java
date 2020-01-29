package com.termux.terminal;

public class ScreenBufferTest extends TerminalTestCase {

	public void testBasics() {
		TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
		assertEquals("", screen.getTranscriptText());
		screen.setChar(0, 0, 'a', 0);
		assertEquals("a", screen.getTranscriptText());
		screen.setChar(0, 0, 'b', 0);
		assertEquals("b", screen.getTranscriptText());
		screen.setChar(2, 0, 'c', 0);
		assertEquals("b c", screen.getTranscriptText());
		screen.setChar(2, 2, 'f', 0);
		assertEquals("b c\n\n  f", screen.getTranscriptText());
		screen.blockSet(0, 0, 2, 2, 'X', 0);
	}

	public void testBlockSet() {
		TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
		screen.blockSet(0, 0, 2, 2, 'X', 0);
		assertEquals("XX\nXX", screen.getTranscriptText());
		screen.blockSet(1, 1, 2, 2, 'Y', 0);
		assertEquals("XX\nXYY\n YY", screen.getTranscriptText());
	}

	public void testGetSelectedText() {
		withTerminalSized(5, 3).enterString("ABCDEFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
		assertEquals("AB", mTerminal.getSelectedText(0, 0, 1, 0));
		assertEquals("BC", mTerminal.getSelectedText(1, 0, 2, 0));
		assertEquals("CDE", mTerminal.getSelectedText(2, 0, 4, 0));
		assertEquals("FG", mTerminal.getSelectedText(0, 1, 1, 1));
		assertEquals("GH", mTerminal.getSelectedText(1, 1, 2, 1));
		assertEquals("HIJ", mTerminal.getSelectedText(2, 1, 4, 1));

		assertEquals("ABCDEFG", mTerminal.getSelectedText(0, 0, 1, 1));
		withTerminalSized(5, 3).enterString("ABCDE\r\nFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
		assertEquals("ABCDE\nFG", mTerminal.getSelectedText(0, 0, 1, 1));
	}

    private class TextWithCursor {
	public String text;
	public int cursorIndex;
    }
    
    private TextWithCursor getTwoLinesAtCursor() {
	TerminalBuffer screen = mTerminal.getScreen();
	int cursorRow = mTerminal.getCursorRow();
	int getRow = cursorRow;
	int cursorCol = mTerminal.getCursorCol();
	int cursorPosition = cursorCol; // for now, in the current line this is right
	String previousLine = "";
	String cursorLine = "";
	
	System.out.println("CRAIG, === GET TWO LINES AT CURSOR ===");
	System.out.println("CRAIG, cursorRow="+cursorRow+", cursorCol="+cursorCol);
	for (int i=0; i<6; i++) {
	    System.out.println("CRAIG, line "+i+" '"+mTerminal.getSelectedText(0,i,1000,i) + "'" + (screen.getLineWrap(i) ? "->" : " . "));
	}

	// If I am reading the TerminalBuffer.java code right I think getSelectedText() will limit the X2 value to the number of columns
	/*
            if (row == selY2) {
                x2 = selX2 + 1;
                if (x2 > columns) x2 = columns;
            } else {
                x2 = columns;
            }
	*/
	if (getRow > 0) {
	    while (getRow > 0 && screen.getLineWrap(getRow-1)) {
		System.out.println("CRAIG, previous line ("+(getRow-1)+") was wrapped.");
		getRow--;
	    }
	} else {
	    System.out.println("CRAIG, no previous line to get");
	}

	System.out.println("CRAIG, getRow="+getRow);
	System.out.println("CRAIG, linewrap? "+screen.getLineWrap(getRow));
	/*
	System.out.println("CRAIG, cursorLine='"+cursorLine+"'");
	cursorLine = mTerminal.getSelectedText(0, getRow, 1000, getRow);
	*/
	
	while (getRow < cursorRow && screen.getLineWrap(getRow)) {
	    System.out.println("CRAIG, getRow="+getRow);
	    System.out.println("CRAIG, linewrap? "+screen.getLineWrap(getRow));
	    String tmp = mTerminal.getSelectedText(0,getRow,1000,getRow);
	    //	    if (getRow < cursorRow) { 
		cursorPosition += tmp.length();
		//	    }
	    cursorLine += tmp;
	    System.out.println("CRAIG, cursorLine='"+cursorLine+"'");
	    getRow++;
	}

	cursorLine += mTerminal.getSelectedText(0, cursorRow, 1000, cursorRow);
	
	// now get lines after
	getRow = cursorRow;
	while (screen.getLineWrap(getRow)) {
	    getRow++;
	    cursorLine += mTerminal.getSelectedText(0,getRow,1000,getRow);
	}
	

	System.out.println("CRAIG, FINAL cursorLine='"+cursorLine+"'");
	System.out.println("CRAIG, FINAL cursorPosition="+cursorPosition);
	TextWithCursor twc = new TextWithCursor();
	twc.text = cursorLine;
	twc.cursorIndex = cursorPosition;
	return twc;
    }
    
    public void xtestGetTwoLines() {
	TextWithCursor twc = new TextWithCursor();

	withTerminalSized(3,6).enterString("abcdef\r\nghijkl");
	placeCursorAndAssert(0,2);
	twc = getTwoLinesAtCursor();
	assertEquals("abcdef", twc.text);
	assertEquals(2, twc.cursorIndex);

	withTerminalSized(3,6).enterString("abc\r\ndefghijkl");
	placeCursorAndAssert(1,2);
	twc = getTwoLinesAtCursor();

	withTerminalSized(3,6).enterString("abc");
	placeCursorAndAssert(0,0);
	twc = getTwoLinesAtCursor();

	withTerminalSized(3,6).enterString("abcdef");
	placeCursorAndAssert(0,0);
	twc = getTwoLinesAtCursor();

	withTerminalSized(3,6).enterString("abcdef");
	placeCursorAndAssert(1,1);
	twc = getTwoLinesAtCursor();

	withTerminalSized(3,6).enterString("abc\r\ndefghijkl");
	placeCursorAndAssert(0,2);
	twc = getTwoLinesAtCursor();
    }
}
