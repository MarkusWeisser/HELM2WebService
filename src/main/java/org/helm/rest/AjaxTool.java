/**
 * *****************************************************************************
 * Copyright C 2015, The Pistoia Alliance
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ****************************************************************************
 */
package org.helm.rest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.helm.chemtoolkit.AbstractChemistryManipulator;
import org.helm.notation2.*;

@Path("/ajaxtool")
public class AjaxTool {

    private static final String DEFAULT_HELM_DIR = System.getProperty("catalina.base") + 
            System.getProperty("file.separator") + "webapps" + 
            System.getProperty("file.separator") + "WebService" + 
            System.getProperty("file.separator") + "hwe" +
            System.getProperty("file.separator") + "db";
    //private static final String DEFAULT_HELM_DIR = System.getProperty("user.home") + System.getProperty("file.separator") + ".helm"; // 
    private static final String DEFAULT_MONOMERS_FILE_NAME = "monomers.txt";
    private static final String DEFAULT_RULES_FILE_NAME = "rules.txt";

    Database monomers = null;
    Database rules = null;

    @GET
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_HTML,
        MediaType.TEXT_PLAIN, MediaType.MULTIPART_FORM_DATA})
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Path("/get")
    public Response CmdGet(@Context HttpServletRequest request) {
        Map<String, String> args = getQueryParameters(request);
        try {
            return OnCmd(args.get("cmd"), args, request);
        } catch (Exception e) {
            return Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + e.getMessage() + ", " + GetTrace(e))).build();
        }
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_HTML,
        MediaType.TEXT_PLAIN, MediaType.MULTIPART_FORM_DATA})
    @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    @Path("/post")
    public Response CmdPost(@Context HttpServletRequest request) {
        String cmd = getQueryParameters(request).get("cmd");
        Map<String, String> args = cmd.equals("openjsd") ? null : getFormParameters(request);
        try {
            return OnCmd(cmd, args, request);
        } catch (Exception e) {
            return Response.status(Response.Status.OK).entity(wrapAjaxError("ERROR: " + e.getMessage() + ", " + GetTrace(e))).build();
        }
    }

    Response OnCmd(String cmd, Map<String, String> items, HttpServletRequest request) throws Exception {
        JSONObject ret = new JSONObject();
        switch (cmd) {
            case "helm.toolkit.monomer.json": {
                ArrayList<JSONObject> ret2 = getToolkitMonomers();
                ret.put("list", ret2);
            }
            break;
            case "helm.toolkit.monomer.downloadjson": {
                ArrayList<JSONObject> ret2 = getToolkitMonomers();
                String s = "org.helm.webeditor.Monomers.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }
            
            case "helm.monomer.del":
                LoadRules();
                ret = monomers.DelRecord(items.get("id"));
                if (ret != null) {
                    try {
                        monomers.Save();
                    } catch (Exception e) {
                        throw e;
                    }
                }
                break;
            case "helm.monomer.load":
                LoadMonomers();
                ret = monomers.LoadRow(items.get("id"));
                break;
            case "helm.monomer.save": {
                LoadMonomers();
                String[] keys = monomers.getKeys();
                String[] row = new String[keys.length];
                for (int i = 0; i < keys.length; ++i) {
                    row[i] = items.get(keys[i]);
                }

                ret = monomers.SaveRecord(row);
                if (ret != null) {
                    try {
                        monomers.Save();
                    } catch (Exception e) {
                        throw e;
                    }
                }
            }
            break;
            case "helm.monomer.suggest":
                break;
            case "helm.monomer.list": {
                LoadMonomers();
                int page = ToInt(items.get("page"));
                int countperpage = ToInt(items.get("countperpage"));
                String polymertype = items.get("polymertype");
                String monomertype = items.get("monomertype");
                String symbol = items.get("symbol");
                ret = monomers.List(page, countperpage, "polymertype", polymertype, "monomertype", monomertype, "symbol", symbol);
            }
            break;
            case "helm.monomer.all": {
                LoadMonomers();
                ret.put("monomers", monomers.AsJSON());
            }            
            break;
            case "helm.monomer.json": {
                LoadMonomers();
                ArrayList<JSONObject> ret2 = monomers.AsJSON();
                ret.put("list", ret2);
            }
            break;
            case "helm.monomer.downloadjson": {
                LoadMonomers();
                ArrayList<JSONObject> ret2 = monomers.AsJSON();
                String s = "org.helm.webeditor.Monomers.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }
            case "helm.monomer.filelocation": {
                ret = new JSONObject();
                ret.put("momomers", DEFAULT_HELM_DIR + System.getProperty("file.separator") + DEFAULT_MONOMERS_FILE_NAME);
            }
            break;

            case "helm.rule.del":
                LoadRules();
                ret = rules.DelRecord(items.get("id"));
                if (ret != null) {
                    try {
                        rules.Save();
                    } catch (Exception e) {
                        throw e;
                    }
                }
                break;
            case "helm.rule.load":
                LoadRules();
                ret = rules.LoadRow(items.get("id"));
                break;
            case "helm.rule.save": {
                LoadRules();
                String[] keys = rules.getKeys();
                String[] row = new String[keys.length];
                for (int i = 0; i < keys.length; ++i) {
                    row[i] = items.get(keys[i]);
                }

                ret = rules.SaveRecord(row);
                if (ret != null) {
                    try {
                        rules.Save();
                    } catch (Exception e) {
                        throw e;
                    }
                }
            }
            break;
            case "helm.rule.list": {
                LoadRules();
                int page = ToInt(items.get("page"));
                int countperpage = ToInt(items.get("countperpage"));
                String category = items.get("category");
                ret = rules.List(page, countperpage, "category", category, null, null, null, null);
            }
            break;
            case "helm.rule.all": {
                LoadRules();
                ret.put("rules", rules.AsJSON());
            }
            break;
            case "helm.rule.downloadjson":
            case "helm.rules.downloadjson": {
                LoadRules();
                ArrayList<JSONObject> ret2 = rules.AsJSON();
                String s = "org.helm.webeditor.RuleSet.loadDB(" + ret2.toString() + ");";
                return Response.status(Response.Status.OK).entity(s).build();
            }

            case "openjsd": {
                ret = new JSONObject();
                Part part = request.getPart("file");
                String filename = getFileName(part);
                String contents = getValue(part);
                ret.put("filename", filename);
                ret.put("base64", Database.EncodeBase64(contents));
                String s = "<html><head></head><body><textarea>" + wrapAjaxResult(ret) + "</textarea></body></html>";
                return Response.status(Response.Status.OK).entity(s).type("text/html").build();
            }
            case "savefile": {
                String filename = items.get("filename");
                String contents = items.get("contents");
                return Response
                        .ok(contents, "application/unknown")
                        .header("content-disposition", "attachment;filename=" + filename)
                        .build();
            }
            case "helm.properties":
                ret = CalculateProperties(items.get("helm"));
                break;
                
            case "cleanup":
                ret = Cleanup(items.get("input"), items.get("inputformat")); 
                break;

            default:
                return Response.status(Response.Status.OK).entity(wrapAjaxError("Unknown cmd: " + cmd)).build();
        }

        return Response.status(Response.Status.OK).entity(wrapAjaxResult(ret)).build();
    }
    
    ArrayList<JSONObject> getToolkitMonomers(){
        ArrayList<JSONObject> ret = new ArrayList<JSONObject>();
        
        try {
            //get monomer database via MonomerFacotry Singleton class
            Map<String, Map<String, Monomer>> allMonomers = MonomerFactory.getInstance().getMonomerDB();

            //loop through polymer type and monomer to build the JSON string
            Set<String> polymerTypes = allMonomers.keySet();
            for (String polymerType : polymerTypes) {
                //momomers for specific polymerType
                Map<String, Monomer> monomers = allMonomers.get(polymerType);
                Set<String> monomerIds = monomers.keySet();
                for (String monomerId : monomerIds) {
                    Monomer monomer = monomers.get(monomerId);
                    ret.add(monomer2Json(monomer));
                }
            }
        } catch (Exception e) {
        }
        
        return ret;
    }
    
    JSONObject monomer2Json(Monomer m) {
        JSONObject ret = new JSONObject();
        ret.put("id", m.getId());
        ret.put("symbol", m.getAlternateId());
        ret.put("name", m.getName());
        ret.put("naturalanalog", m.getNaturalAnalog());
        ret.put("molfile", m.getMolfile());
        ret.put("smiles", m.getCanSMILES());
        ret.put("polymertype", m.getPolymerType());
        ret.put("monomertype", m.getMonomerType());
        
        List<Attachment> al = m.getAttachmentList();
        List<String> l = new ArrayList();
        for (Attachment a : al) {
            ret.put(a.getLabel().toLowerCase(), a.getCapGroupName());
        }
        return ret;
    }
    
    private String getFileName(final Part part) {
        final String partHeader = part.getHeader("content-disposition");
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(
                        content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    static JSONObject Cleanup(String q, String inputformat) {
        if (StringUtils.isEmpty(q)) {
            return null;
        }
        
        JSONObject ret = new JSONObject();
        try {
            org.helm.chemtoolkit.cdk.CDKManipulator m = new org.helm.chemtoolkit.cdk.CDKManipulator();
            String molfile = null;
            if (inputformat != null && (inputformat.equals("mol") || inputformat.equals("molfile"))) {
                String smiles = m.convert(q, AbstractChemistryManipulator.StType.MOLFILE);
                molfile = m.convert(smiles, AbstractChemistryManipulator.StType.SMILES);
            }
            else {
                molfile = m.convert(q, AbstractChemistryManipulator.StType.SMILES);
            }
            ret.put("output", molfile);
        } catch (Exception e) {
        }
        return ret;
    }

    static JSONObject CalculateProperties(String helm) {
        if (StringUtils.isEmpty(helm)) {
            return null;
        }

        org.helm.notation2.tools.WebService webservice = new org.helm.notation2.tools.WebService();

        JSONObject ret = new JSONObject();
        try {
            ret.put("helm", helm);
            ret.put("mw", webservice.calculateMolecularWeight(helm));
            ret.put("mf", webservice.getMolecularFormula(helm));
            ret.put("ec", webservice.calculateExtinctionCoefficient(helm));
        } catch (Exception e) {
        }
        return ret;
    }

    static String getValue(Part part) throws IOException {
        if (part == null) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream(), "UTF-8"));
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[1024];
        for (int length = 0; (length = reader.read(buffer)) > 0;) {
            value.append(buffer, 0, length);
        }
        return value.toString();
    }

    static String GetTrace(Exception e) {
        StackTraceElement[] list = e.getStackTrace();
        if (list == null) {
            return null;
        }

        String s = "";
        for (int i = 0; i < list.length; ++i) {
            s += list[i].getFileName() + "->" + list[i].getClassName() + "->" + list[i].getMethodName() + ": line " + list[i].getLineNumber() + "|";
        }
        return s;
    }

    void LoadMonomers() {
        if (monomers == null) {
            String[] cols = {"id", "symbol", "name", "naturalanalog", "molfile", "smiles", "polymertype", "monomertype", "r1", "r2", "r3", "r4", "r5", "author", "createddate"};
            seedDatabase(DEFAULT_MONOMERS_FILE_NAME);
            monomers = new Database(DEFAULT_HELM_DIR + System.getProperty("file.separator") + DEFAULT_MONOMERS_FILE_NAME, cols);
        }
    }

    void LoadRules() {
        if (rules == null) {
            String[] cols = {"id", "name", "description", "script", "author", "category" };
            seedDatabase(DEFAULT_RULES_FILE_NAME);
            rules = new Database(DEFAULT_HELM_DIR + System.getProperty("file.separator") + DEFAULT_RULES_FILE_NAME, cols);
        }
    }

    void seedDatabase(String fileName) {
        File f = new File(DEFAULT_HELM_DIR + System.getProperty("file.separator") + fileName);
        BufferedReader reader = null;
        BufferedWriter writer = null;
        if (!f.exists()) {
            try {
                File dir = new File(DEFAULT_HELM_DIR);
                if (!dir.exists()) {
                    dir.mkdir();
                }

                f.createNewFile();
                InputStream in = AjaxTool.class.getResourceAsStream("/" + fileName);
                reader = new BufferedReader(new InputStreamReader(in));
                writer = new BufferedWriter(new FileWriter(f));

                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + System.getProperty("line.separator"));
                }
            } catch (IOException ex) {
                Logger.getLogger(AjaxTool.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (null != reader) {
                        reader.close();
                    }
                    if (null != writer) {
                        writer.close();
                    }
                } catch (Exception e) {

                }

            }
        }
    }

    Map<String, String> getFormParameters(HttpServletRequest request) {
        Map<String, String> dict = new HashMap<>();
        Map<String, String> ret = new HashMap<>();
        String q = null;
        try {
            q = IOUtils.toString(request.getInputStream());
        } catch (Exception e) {
        }

        if (q != null && q.length() > 0) {
            dict = parseQueryString(q);
        }

        for (String k : dict.keySet()) {
            String v = dict.get(k);
            ret.put(k, v == null || v.isEmpty() ? null : v);
        }

        return ret;
    }

    Map<String, String> getQueryParameters(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return parseQueryString(queryString);
    }

    Map<String, String> parseQueryString(String queryString) {
        Map<String, String> queryParameters = new HashMap<>();
        if (StringUtils.isEmpty(queryString)) {
            return queryParameters;
        }

        String[] parameters = queryString.split("&");
        for (String parameter : parameters) {
            String[] keyValuePair = parameter.split("=");
            String v = keyValuePair.length < 2 ? null : keyValuePair[1];
            if (v != null) {
                try {
                    v = java.net.URLDecoder.decode(v, "UTF-8");
                } catch (Exception e) {
                }
            }
            queryParameters.put(keyValuePair[0], v);
        }
        return queryParameters;
    }

    static int ToInt(String s) {
        try {
            if (s == null || s.length() == 0) {
                return 0;
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    // tool function to wrap HELM Editor acceptable json results
    public static String wrapAjaxResult(JSONObject ret) {
        JSONObject json = new JSONObject();
        json.put("succeed", true);
        json.put("ret", ret);
        return json.toString();
    }

    // tool function to wrap HELM Editor acceptable json results
    public static String wrapAjaxResult(java.util.ArrayList ret) {
        JSONObject json = new JSONObject();
        json.put("succeed", true);
        json.put("ret", ret);
        return json.toString();
    }

    public static String wrapAjaxError(String error) {
        JSONObject json = new JSONObject();
        json.put("succeed", false);
        json.put("error", error);
        return json.toString();
    }
}
