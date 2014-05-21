/**
 * Copyright (c) 2005, 2008 Danny Yoo (http://bitbucket.org/jlanchas/aho-corasick/)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of the Carnegie Institution of Washington nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/
package org.arabidopsis.ahocorasick;

import junit.framework.TestCase;

public class TestState extends TestCase {
	public void testSimpleExtension() {
		State s = new State(0);
		State s2 = s.extend('a');
		assertTrue(s2 != s && s2 != null);
		assertEquals(2, s.size());
	}

	public void testSimpleExtensionSparse() {
		State s = new State(50);
		State s2 = s.extend('a');
		assertTrue(s2 != s && s2 != null);
		assertEquals(2, s.size());
	}

	public void testSingleState() {
		State s = new State(0);
		assertEquals(1, s.size());
	}

	public void testSingleStateSparse() {
		State s = new State(50);
		assertEquals(1, s.size());
	}

	public void testExtendAll() {
		State s = new State(0);
		State s2 = s.extendAll("hello world".toCharArray());
		assertEquals(12, s.size());
	}

	public void testExtendAllTwiceDoesntAddMoreStates() {
		State s = new State(0);
		State s2 = s.extendAll("hello world".toCharArray());
		State s3 = s.extendAll("hello world".toCharArray());
		assertEquals(12, s.size());
		assertTrue(s2 == s3);
	}

	public void testExtendAllTwiceDoesntAddMoreStatesSparse() {
		State s = new State(50);
		State s2 = s.extendAll("hello world".toCharArray());
		State s3 = s.extendAll("hello world".toCharArray());
		assertEquals(12, s.size());
		assertTrue(s2 == s3);
	}

	public void testAddingALotOfStatesIsOk() {
		State s = new State(0);
		for (int i = 0; i < 256; i++)
			s.extend((char) i);
		assertEquals(257, s.size());
	}

	public void testAddingALotOfStatesIsOkOnSparseRep() {
		State s = new State(50);
		for (int i = 0; i < 256; i++)
			s.extend((char) i);
		assertEquals(257, s.size());
	}

}