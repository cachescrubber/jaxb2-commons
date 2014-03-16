/*
 * Copyright 2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.tools.xjc.addon.commons_lang;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * Automatically generates the toString(), hashCode() and equals() methods 
 * using Jakarta's commons-lang.
 * <p>
 * Supports the optional ToStringStyle command line parameter to specify 
 * the style for use within the toString method.
 * <p>
 * <pre>
 * Example 1:
 *  
 *     -Xcommons-lang
 *     -Xcommons-lang:ToStringStyle=SIMPLE_STYLE
 *     
 *     to specify the use of 
 * 
 *     org.apache.commons.lang.builder.ToStringStyle.SIMPLE_STYLE
 *     
 * Example 2:
 *  
 *     -Xcommons-lang
 *     -Xcommons-lang:ToStringStyle=my.CustomToStringStyle
 *     
 *     to specify the use of 
 * 
 *     my.CustomToStringStyle, which must be a subclass of 
 *     
 *     org.apache.commons.lang.builder.ToStringStyle, and contains a public no-arg constructor.
 *     
 * </pre>
 * <p>
 * The default ToStringStyle adopted by this plugin is MULTI_LINE_STYLE.
 *     
 * @see org.apache.commons.lang.builder.ToStringStyle 
 * @author Hanson Char
 */
public class XjcCommonsLangPlugin extends Plugin
{
    private static final String TOSTRING_STYLE_PARAM = "-Xcommons-lang:ToStringStyle=";
    private String toStringStyle = "MULTI_LINE_STYLE";
    private Class<?> customToStringStyle;
    
    @Override
    public String getOptionName()
    {
        return "Xcommons-lang";
    }

    @Override
    public String getUsage()
    {
        return "  -Xcommons-lang        :  generate toString(), hashCode() and equals() for generated code using Jakarta's common-lang\n"
             + " [-Xcommons-lang:ToStringStyle=MULTI_LINE_STYLE\n\t"
             + "| DEFAULT_STYLE\n\t"
             + "| NO_FIELD_NAMES_STYLE\n\t"
             + "| SHORT_PREFIX_STYLE\n\t"
             + "| SIMPLE_STYLE\n\t"
             + "| <Fully qualified class name of a ToStringStyle subtype>]\n"
             ;
    }

    @Override
    public boolean run(Outline outline, 
        @SuppressWarnings("unused") Options opt, 
        @SuppressWarnings("unused") ErrorHandler errorHandler)
    {
        // Process every pojo class generated by jaxb
        for (ClassOutline classOutline : outline.getClasses()) {
            JDefinedClass implClass = classOutline.implClass;
            this.createToStringMethod(implClass);
            this.createEqualsMethod(implClass);
            this.createHashCodeMethod(implClass);
        }
        return true;
    }
    
    private void createToStringMethod(JDefinedClass implClass) 
    {
        JCodeModel codeModel = implClass.owner();
        JMethod toStringMethod = 
            implClass.method(JMod.PUBLIC, codeModel.ref(String.class), "toString");
        // Annotate with @Override
        toStringMethod.annotate(Override.class);
        final JExpression toStringStyleExpr =
                customToStringStyle == null
              ? codeModel.ref(ToStringStyle.class)
                         .staticRef(toStringStyle)
              : JExpr._new(
                      codeModel.ref(customToStringStyle))
              ;
        // Invoke ToStringBuilder.reflectionToString(Object,StringStyle)
        toStringMethod.body()
                      ._return(
                              codeModel.ref(ToStringBuilder.class)
                                       .staticInvoke("reflectionToString")
                                       .arg(JExpr._this())
                                       .arg(toStringStyleExpr)
                              );
        return;
    }
    
    private void createEqualsMethod(JDefinedClass implClass) 
    {
        JCodeModel codeModel = implClass.owner();
        JMethod toStringMethod = 
            implClass.method(JMod.PUBLIC, codeModel.BOOLEAN, "equals");
        JVar that = toStringMethod.param(Object.class, "that");
        // Annotate with @Override
        toStringMethod.annotate(Override.class);
        // Invoke EqualsBuilder.reflectionEquals(Object,Object);
        toStringMethod.body()._return(
            codeModel.ref(EqualsBuilder.class)
                     .staticInvoke("reflectionEquals")
                     .arg(JExpr._this())
                     .arg(that)
        );
        return;
    }
    
    private void createHashCodeMethod(JDefinedClass implClass) 
    {
        JCodeModel codeModel = implClass.owner();
        JMethod toStringMethod = 
            implClass.method(JMod.PUBLIC, codeModel.INT, "hashCode");
        // Annotate with @Override
        toStringMethod.annotate(Override.class);
        // Invoke EqualsBuilder.reflectionHashCode(Object);
        toStringMethod.body()._return(
            codeModel.ref(HashCodeBuilder.class)
                     .staticInvoke("reflectionHashCode")
                     .arg(JExpr._this())
        );
        return;
    }
    
    @Override
    public int parseArgument(Options opt, String[] args, int i)
        throws BadCommandLineException
    {
        // eg. -Xcommons-lang ToStringStyle=SIMPLE_STYLE
        String arg = args[i].trim();
            
        if (arg.startsWith(TOSTRING_STYLE_PARAM))
        {
            toStringStyle = arg.substring(TOSTRING_STYLE_PARAM.length());
            try {
                ToStringStyle.class.getField(toStringStyle);
                return 1;
            } catch (SecurityException e) {
                throw new BadCommandLineException(e.getMessage());
            } catch (NoSuchFieldException ignore) {
            }
            try {
                customToStringStyle = Class.forName(toStringStyle);
            } catch (ClassNotFoundException e) {
                throw new BadCommandLineException(e.getMessage());
            }
            return 1;
        }
        return 0;
    }
}
