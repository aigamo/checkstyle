////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2005  Oliver Burn
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.doclets;

import com.sun.javadoc.RootDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.PackageDoc;

import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Doclet which is used to extract Anakia input files from the
 * Javadoc of Check implementations, so the Check's docs are
 * autogenerated.
 *
 * @author lkuehne
 */
public final class CheckDocsDoclet
{
    /** javadoc command line option for dest dir. */
    private static final String DEST_DIR_OPT = "-d";

    /** Maps package names to DocumentationPages. */
    private static Map sDocumentationPages = new HashMap();

    /**
     * Collects the content of a page in the resulting documentation.
     * Each Java package will result in one doc page.
     * */
    private static final class DocumentationPage
    {
        /** The javadoc for the corresponding java package. */
        private PackageDoc mPackageDoc;

        /** maps check names to class doc, sorted. */
        private Map mChecks = new TreeMap();

        /**
         * Creates a new Documentation page.
         * @param aPackageDoc package.html for the corresponding java package
         */
        private DocumentationPage(PackageDoc aPackageDoc)
        {
            mPackageDoc = aPackageDoc;
        }

        /** @return the package name of the corresponding java package. */
        private String getPackageName()
        {
            return mPackageDoc.name();
        }

        /**
         * Register a Checkstyle check's class documentation for inclusion
         * on the DocumentationPage.
         *
         * @param aClassDoc the check's documentation as extracted by javadoc
         */
        private void addCheck(ClassDoc aClassDoc)
        {
            String checkName = getCheckName(aClassDoc);
            mChecks.put(checkName, aClassDoc);
        }

        /**
         * The first sentence of the check description.
         * Checks must have been registered with {@link #addCheck}.
         *
         * @param aCheckName the name of the check, e.g. EmptyStatement
         * @return The first sentence of the check description.
         */
        private String getDescription(String aCheckName)
        {
            ClassDoc classDoc = (ClassDoc) mChecks.get(aCheckName);
            final Tag[] tags = classDoc.firstSentenceTags();
            StringBuffer buf = new StringBuffer();
            if (tags.length > 0) {
                buf.append(tags[0].text());
            }
            removeOpeningParagraphTag(buf);
            return buf.toString();
        }

        /**
         * Removes an opening p tag from a StringBuffer.
         * @param aText the text to process
         */
        private void removeOpeningParagraphTag(final StringBuffer aText)
        {
            final String openTag = "<p>";
            final int tagLen = openTag.length();
            if (aText.length() > tagLen
                    && aText.substring(0, tagLen).equals(openTag))
            {
                aText.delete(0, tagLen);
            }
        }
    }

    /**
     * Returns the official name of a check.
     *
     * @param aClassDoc the the check's documentation as extracted by javadoc
     * @return the check name, e.g. "IllegalImport" for
     * the "c.p.t.c.c.i.IllegalImportCheck" class.
     */
    private static String getCheckName(final ClassDoc aClassDoc)
    {
        final String strippedClassName = aClassDoc.typeName();
        final String checkName = strippedClassName.endsWith("Check")
                ? strippedClassName.substring(
                        0, strippedClassName.length() - "Check".length())
                : strippedClassName;
        return checkName;
    }

    /**
     * Writes the opening tags of an xdoc.
     * @param aPrintWriter you guessed it ... the target to print to :)
     * @param aTitle the title to use for the document.
     */
    private static void writeXdocsHeader(
            final PrintWriter aPrintWriter,
            final String aTitle)
    {
        aPrintWriter.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
        aPrintWriter.println("<document>");
        aPrintWriter.println("<properties>");
        aPrintWriter.println("<title>" + aTitle + "</title>");
        aPrintWriter.println("<author "
                + "email=\"checkstyle-devel@lists.sourceforge.net"
                + "\">Checkstyle Development Team</author>");
        aPrintWriter.println("</properties>");
        aPrintWriter.println("<body>");
        aPrintWriter.flush();
    }

    /**
     * Writes the closing tags of an xdoc document.
     * @param aPrintWriter you guessed it ... the target to print to :)
     */
    private static void writeXdocsFooter(final PrintWriter aPrintWriter)
    {
        aPrintWriter.println("</body>");
        aPrintWriter.println("</document>");
        aPrintWriter.flush();
    }

    /**
     * Finds or creates a documentation page where the content of
     * a check's class documentation should be included.
     *
     * @param aClassDoc the class documentation
     * @return the found or created page, registered in
     * {@link #sDocumentationPages}
     */
    private static DocumentationPage findDocumentationPage(ClassDoc aClassDoc)
    {
        final PackageDoc packageDoc = aClassDoc.containingPackage();
        final String packageName = packageDoc.name();
        DocumentationPage page =
                (DocumentationPage) sDocumentationPages.get(packageName);

        if (page == null) {
            page = new DocumentationPage(packageDoc);
            sDocumentationPages.put(packageName, page);
        }

        return page;
    }

    /**
     * Doclet entry point.
     * @param aRoot parsed javadoc of all java files passed to the javadoc task
     * @return true (TODO: semantics of the return value is not clear to me)
     * @throws IOException if there are problems writing output
     */
    public static boolean start(RootDoc aRoot) throws IOException
    {
        final ClassDoc[] classDocs = aRoot.classes();
        final Map allChecks = new TreeMap();
        for (int i = 0; i < classDocs.length; i++) {
            ClassDoc classDoc = classDocs[i];
            // TODO: introduce a "CheckstyleModule" interface
            // so we can do better in the next line...
            if (classDoc.typeName().endsWith("Check")
                    && !classDoc.isAbstract())
            {
                DocumentationPage page = findDocumentationPage(classDoc);
                page.addCheck(classDoc);
                allChecks.put(getCheckName(classDoc), page);
            }
        }

        // TODO: close files in finally blocks

        final File destDir = new File(getDestDir(aRoot.options()));

        final File checksIndexFile = new File(destDir, "availablechecks.xml");
        PrintWriter fileWriter = new PrintWriter(
                new FileWriter(checksIndexFile));
        writeXdocsHeader(fileWriter, "Available Checks");

        fileWriter.println("<p>Checkstyle provides many checks that you can"
                + " apply to your sourcecode. Below is an alphabetical"
                + " reference, the site navigation menu provides a reference"
                + " organized by functionality.</p>");
        fileWriter.println("<table>");

        for (Iterator it = allChecks.keySet().iterator(); it.hasNext();) {
            String checkName = (String) it.next();
            DocumentationPage page =
                    (DocumentationPage) allChecks.get(checkName);
            String descr = page.getDescription(checkName);
            fileWriter.println("<tr>"
                    + "<td><a href=\""
                    + "config_" + getPageName(page) + ".html#" + checkName
                    + "\">" + checkName + "</a></td><td>"
                    + descr
                    + "</td></tr>");
        }
        fileWriter.println("</table>");
        writeXdocsFooter(fileWriter);
        fileWriter.close();

        return true;
    }

    /**
     * Calculates the human readable page name for a doc page.
     *
     * @param aPage the doc page.
     * @return the human readable page name for the doc page.
     */
    private static String getPageName(DocumentationPage aPage)
    {
        final String packageName = aPage.getPackageName();
        String pageName =
                packageName.substring(packageName.lastIndexOf('.') + 1);
        if ("checks".equals(pageName)) {
            return "misc";
        }
        return pageName;
    }

    /**
     * Return the destination directory for this Javadoc run.
     * @param aOptions Javadoc commandline options
     * @return the dest dir specified on the command line (or ant task)
     */
    public static String getDestDir(String[][] aOptions)
    {
        for (int i = 0; i < aOptions.length; i++) {
            String[] opt = aOptions[i];
            if (DEST_DIR_OPT.equalsIgnoreCase(opt[0])) {
                return opt[1];
            }
        }
        return null; // TODO: throw exception here ???
    }

    /**
     * Returns option length (how many parts are in option).
     * @param aOption option name to process
     * @return option length (how many parts are in option).
     */
    public static int optionLength(String aOption)
    {
        if (DEST_DIR_OPT.equals(aOption)) {
            return 2;
        }
        return 0;
    }

}
