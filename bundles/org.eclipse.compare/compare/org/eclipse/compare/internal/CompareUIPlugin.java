/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.compare.internal;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import java.util.List;

import org.eclipse.ui.*;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;

import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.*;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.compare.*;
import org.eclipse.compare.structuremergeviewer.*;


/**
 * The Compare UI plug-in defines the entry point to initiate a configurable
 * compare operation on arbitrary resources. The result of the compare
 * is opened into a compare editor where the details can be browsed and
 * edited in dynamically selected structure and content viewers.
 * <p>
 * The Compare UI provides a registry for content and structure compare viewers,
 * which is initialized from extensions contributed to extension points
 * declared by this plug-in.
 * <p>
 * This class is the plug-in runtime class for the 
 * <code>"org.eclipse.compare"</code> plug-in.
 * </p>
 */
public final class CompareUIPlugin extends AbstractUIPlugin {
    
    static class CompareRegistry {
        
		private final static String ID_ATTRIBUTE= "id"; //$NON-NLS-1$
    		private final static String EXTENSIONS_ATTRIBUTE= "extensions"; //$NON-NLS-1$
       	private final static String CONTENT_TYPE_ID_ATTRIBUTE= "contentTypeId"; //$NON-NLS-1$
 


        private HashMap fIdMap;
        private HashMap fExtensionMap;
        private HashMap fContentTypeBindings;		// maps content type bindings to datas
        
 
	    	void register(IConfigurationElement element, Object data) {
	    	    String id= element.getAttribute(ID_ATTRIBUTE);
	    	    if (id != null) {      
	    	        if (fIdMap == null)
	    	            fIdMap= new HashMap();
	    	        fIdMap.put(id, data);
	    	    }
	    	    
	    	    String types= element.getAttribute(EXTENSIONS_ATTRIBUTE);
	    	    if (types != null) {
	    	        if (fExtensionMap == null)
	    	            fExtensionMap= new HashMap();
		    		StringTokenizer tokenizer= new StringTokenizer(types, ","); //$NON-NLS-1$
		    		while (tokenizer.hasMoreElements()) {
		    			String extension= tokenizer.nextToken().trim();
		    			fExtensionMap.put(normalizeCase(extension), data);
		    		}
	    	    }
	    	}

	    	void createBinding(IConfigurationElement element, String idAttributeName) {
            String type= element.getAttribute(CONTENT_TYPE_ID_ATTRIBUTE);
            String id= element.getAttribute(idAttributeName);
            if (id == null)
                logErrorMessage(Utilities.getFormattedString("CompareUIPlugin.targetIdAttributeMissing", idAttributeName)); //$NON-NLS-1$
            if (type != null && id != null && fIdMap != null) {
                Object o= fIdMap.get(id);
                if (o != null) {
                    IContentType ct= fgContentTypeManager.getContentType(type);
                    if (ct != null) {
                        if (fContentTypeBindings == null)
                            fContentTypeBindings= new HashMap();
                        fContentTypeBindings.put(ct, o);
                    } else {
                        logErrorMessage(Utilities.getFormattedString("CompareUIPlugin.contentTypeNotFound", type)); //$NON-NLS-1$
                    }
                } else {
                    logErrorMessage(Utilities.getFormattedString("CompareUIPlugin.targetNotFound", id)); //$NON-NLS-1$
                }
            }
	    	}

	    	Object search(IContentType type) {
	    	    if (type != null && fContentTypeBindings != null) {
		    	    Object b= fContentTypeBindings.get(type);
		    	    if (b != null)
		    	        return b;
	            Iterator iter= fContentTypeBindings.keySet().iterator();
	            while (iter.hasNext()) {
	                IContentType ct= (IContentType) iter.next();
	                if (type.isKindOf(ct)) {
	                    return fContentTypeBindings.get(ct);
	                }
	            }
	    	    }
	    	    return null;
	    	}
	    	
	    	Object search(String extension) {
	    	    if (fExtensionMap != null)
	    	        return fExtensionMap.get(normalizeCase(extension));
	    	    return null;
	    	}
    }
	
	public static final String DTOOL_NEXT= "dlcl16/next_nav.gif";	//$NON-NLS-1$
	public static final String ETOOL_NEXT= "elcl16/next_nav.gif";	//$NON-NLS-1$
	public static final String CTOOL_NEXT= ETOOL_NEXT;
	
	public static final String DTOOL_PREV= "dlcl16/prev_nav.gif";	//$NON-NLS-1$
	public static final String ETOOL_PREV= "elcl16/prev_nav.gif";	//$NON-NLS-1$
	public static final String CTOOL_PREV= ETOOL_PREV;
				
	/** Status code describing an internal error */
	public static final int INTERNAL_ERROR= 1;

	private static boolean NORMALIZE_CASE= true;

	public static final String PLUGIN_ID= "org.eclipse.compare"; //$NON-NLS-1$
	
	private static final String BINARY_TYPE= "binary"; //$NON-NLS-1$

	private static final String STREAM_MERGER_EXTENSION_POINT= "streamMergers"; //$NON-NLS-1$
		private static final String STREAM_MERGER= "streamMerger"; //$NON-NLS-1$
		private static final String STREAM_MERGER_ID_ATTRIBUTE= "streamMergerId"; //$NON-NLS-1$
	private static final String STRUCTURE_CREATOR_EXTENSION_POINT= "structureCreators"; //$NON-NLS-1$
		private static final String STRUCTURE_CREATOR= "structureCreator"; //$NON-NLS-1$
		private static final String STRUCTURE_CREATOR_ID_ATTRIBUTE= "structureCreatorId"; //$NON-NLS-1$
		
	private static final String VIEWER_TAG= "viewer"; //$NON-NLS-1$
	private static final String STRUCTURE_MERGE_VIEWER_EXTENSION_POINT= "structureMergeViewers"; //$NON-NLS-1$
		private static final String STRUCTURE_MERGE_VIEWER_ID_ATTRIBUTE= "structureMergeViewerId"; //$NON-NLS-1$
	private static final String CONTENT_MERGE_VIEWER_EXTENSION_POINT= "contentMergeViewers"; //$NON-NLS-1$
		private static final String CONTENT_MERGE_VIEWER_ID_ATTRIBUTE= "contentMergeViewerId"; //$NON-NLS-1$
	private static final String CONTENT_VIEWER_EXTENSION_POINT= "contentViewers"; //$NON-NLS-1$
		private static final String CONTENT_VIEWER_ID_ATTRIBUTE= "contentViewerId"; //$NON-NLS-1$

	private static final String CONTENT_TYPE_BINDING= "contentTypeBinding"; //$NON-NLS-1$


  	private static final String COMPARE_EDITOR= PLUGIN_ID + ".CompareEditor"; //$NON-NLS-1$
	
	private static final String STRUCTUREVIEWER_ALIASES_PREFERENCE_NAME= "StructureViewerAliases";	//$NON-NLS-1$

	// content type
	private static final IContentTypeManager fgContentTypeManager= Platform.getContentTypeManager();

	/**
	 * The plugin singleton.
	 */
	private static CompareUIPlugin fgComparePlugin;
	
	/** Maps type to icons */
	private static Map fgImages= new Hashtable(10);
	/** Maps type to ImageDescriptors */
	private static Map fgImageDescriptors= new Hashtable(10);
	/** Maps ImageDescriptors to Images */
	private static Map fgImages2= new Hashtable(10);
	
	private static List fgDisposeOnShutdownImages= new ArrayList();
	
	private ResourceBundle fResourceBundle;

	private CompareRegistry fStreamMergers= new CompareRegistry();
	private CompareRegistry fStructureCreators= new CompareRegistry();
	private CompareRegistry fStructureMergeViewers= new CompareRegistry();
	private CompareRegistry fContentViewers= new CompareRegistry();
	private CompareRegistry fContentMergeViewers= new CompareRegistry();

	private Map fStructureViewerAliases= new Hashtable(10);
	private CompareFilter fFilter;
	private IPropertyChangeListener fPropertyChangeListener;
	
	/**
	 * Creates the <code>CompareUIPlugin</code> object and registers all
	 * structure creators, content merge viewers, and structure merge viewers
	 * contributed to this plug-in's extension points.
	 * <p>
	 * Note that instances of plug-in runtime classes are automatically created 
	 * by the platform in the course of plug-in activation.
	 * </p>
	 *
	 * @param descriptor the plug-in descriptor
	 */
	public CompareUIPlugin(IPluginDescriptor descriptor) {
		super(descriptor);
				
		Assert.isTrue(fgComparePlugin == null);
		fgComparePlugin= this;
		
		ComparePreferencePage.initDefaults(getPreferenceStore());		
		
		fResourceBundle= descriptor.getResourceBundle();
		registerExtensions();
		initPreferenceStore();
	}
	
//	/**
//	 * @see AbstractUIPlugin#initializeDefaultPreferences
//	 */
//	protected void initializeDefaultPreferences(IPreferenceStore store) {
//		super.initializeDefaultPreferences(store);
//		ComparePreferencePage.initDefaults(store);		
//	}
		
	/**
	 * Returns the singleton instance of this plug-in runtime class.
	 *
	 * @return the compare plug-in instance
	 */
	public static CompareUIPlugin getDefault() {
		return fgComparePlugin;
	}
	
	/**
	 * Returns this plug-in's resource bundle.
	 *
	 * @return the plugin's resource bundle
	 */
	public ResourceBundle getResourceBundle() {
		return getDefault().fResourceBundle;
	}
	
	/**
	 * Returns this plug-in's unique identifier.
	 *
	 * @return the plugin's unique identifier
	 */
	public static String getPluginId() {
		return getDefault().getDescriptor().getUniqueIdentifier();
	}

	/**
	 * Registers all stream mergers, structure creators, content merge viewers, and structure merge viewers
	 * that are found in the XML plugin files.
	 */
	private void registerExtensions() {
		IPluginRegistry registry= Platform.getPluginRegistry();
		
		// collect all IStreamMergers
		IConfigurationElement[] elements= registry.getConfigurationElementsFor(PLUGIN_ID, STREAM_MERGER_EXTENSION_POINT);
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
	    		if (STREAM_MERGER.equals(element.getName()))
				fStreamMergers.register(element, new StreamMergerDescriptor(element));
		}
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
	    		if (CONTENT_TYPE_BINDING.equals(element.getName()))
	    		    fStreamMergers.createBinding(element, STREAM_MERGER_ID_ATTRIBUTE);
		}
				
		// collect all IStructureCreators
		elements= registry.getConfigurationElementsFor(PLUGIN_ID, STRUCTURE_CREATOR_EXTENSION_POINT);
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    String name= element.getName();
		    if (!CONTENT_TYPE_BINDING.equals(name)) {
		        if (!STRUCTURE_CREATOR.equals(name))
	                logErrorMessage(Utilities.getFormattedString("CompareUIPlugin.unexpectedTag", name, STRUCTURE_CREATOR)); //$NON-NLS-1$		            
		        fStructureCreators.register(element, new StructureCreatorDescriptor(element));
		    }
		}
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    if (CONTENT_TYPE_BINDING.equals(element.getName()))
		        fStructureCreators.createBinding(element, STRUCTURE_CREATOR_ID_ATTRIBUTE);
		}
				
		// collect all viewers which define the structure mergeviewer extension point
		elements= registry.getConfigurationElementsFor(PLUGIN_ID, STRUCTURE_MERGE_VIEWER_EXTENSION_POINT);
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    String name= element.getName();
		    if (!CONTENT_TYPE_BINDING.equals(name)) {
		        if (!VIEWER_TAG.equals(name))
	                logErrorMessage(Utilities.getFormattedString("CompareUIPlugin.unexpectedTag", name, VIEWER_TAG)); //$NON-NLS-1$		            
		        fStructureMergeViewers.register(element, new ViewerDescriptor(element));
		    }
		}
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    if (CONTENT_TYPE_BINDING.equals(element.getName()))
		        fStructureMergeViewers.createBinding(element, STRUCTURE_MERGE_VIEWER_ID_ATTRIBUTE);
		}
		
		// collect all viewers which define the content mergeviewer extension point
		elements= registry.getConfigurationElementsFor(PLUGIN_ID, CONTENT_MERGE_VIEWER_EXTENSION_POINT);
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    String name= element.getName();
		    if (!CONTENT_TYPE_BINDING.equals(name)) {
		        if (!VIEWER_TAG.equals(name))
	                logErrorMessage(Utilities.getFormattedString("CompareUIPlugin.unexpectedTag", name, VIEWER_TAG)); //$NON-NLS-1$		            
		        fContentMergeViewers.register(element, new ViewerDescriptor(element));
		    }
		}
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    if (CONTENT_TYPE_BINDING.equals(element.getName()))
		        fContentMergeViewers.createBinding(element, CONTENT_MERGE_VIEWER_ID_ATTRIBUTE);
		}
		
		// collect all viewers which define the content viewer extension point
		elements= registry.getConfigurationElementsFor(PLUGIN_ID, CONTENT_VIEWER_EXTENSION_POINT);
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    String name= element.getName();
		    if (!CONTENT_TYPE_BINDING.equals(name)) {
		        if (!VIEWER_TAG.equals(name))
	                logErrorMessage(Utilities.getFormattedString("CompareUIPlugin.unexpectedTag", name, VIEWER_TAG)); //$NON-NLS-1$		            
		        fContentViewers.register(element, new ViewerDescriptor(element));
		    }
		}
		for (int i= 0; i < elements.length; i++) {
		    IConfigurationElement element= elements[i];
		    if (CONTENT_TYPE_BINDING.equals(element.getName()))
		        fContentViewers.createBinding(element, CONTENT_VIEWER_ID_ATTRIBUTE);
		}
	}
	
	public static IWorkbench getActiveWorkbench() {
		CompareUIPlugin plugin= getDefault();
		if (plugin == null)
			return null;
		return plugin.getWorkbench();
	}
	
	public static IWorkbenchWindow getActiveWorkbenchWindow() {
		IWorkbench workbench= getActiveWorkbench();
		if (workbench == null)
			return null;	
		return workbench.getActiveWorkbenchWindow();
	}
	
	/**
	 * Returns the active workkbench page or <code>null</code> if
	 * no active workkbench page can be determined.
	 *
	 * @return the active workkbench page or <code>null</code> if
	 * 	no active workkbench page can be determined
	 */
	private static IWorkbenchPage getActivePage() {
		IWorkbenchWindow window= getActiveWorkbenchWindow();
		if (window == null)
			return null;
		return window.getActivePage();
	}
	
	/**
	 * Returns the SWT Shell of the active workbench window or <code>null</code> if
	 * no workbench window is active.
	 *
	 * @return the SWT Shell of the active workbench window, or <code>null</code> if
	 * 	no workbench window is active
	 */
	public static Shell getShell() {
		IWorkbenchWindow window= getActiveWorkbenchWindow();
		if (window == null)
			return null;
		return window.getShell();
	}

	/**
	 * Registers the given image for being disposed when this plug-in is shutdown.
	 *
	 * @param image the image to register for disposal
	 */
	public static void disposeOnShutdown(Image image) {
		if (image != null)
			fgDisposeOnShutdownImages.add(image);
	}
	
	/* (non-Javadoc)
	 * Method declared on Plugin.
	 * Frees all resources of the compare plug-in.
	 */
	public void shutdown() throws CoreException {
			
		/*
		 * Converts the aliases into a single string before they are stored
		 * in the preference store.
		 * The format is:
		 * <key> '.' <alias> ' ' <key> '.' <alias> ...
		 */
		IPreferenceStore ps= getPreferenceStore();
		if (ps != null) {
			StringBuffer sb= new StringBuffer();
			Iterator iter= fStructureViewerAliases.keySet().iterator();
			while (iter.hasNext()) {
				String key= (String) iter.next();
				String alias= (String) fStructureViewerAliases.get(key);
				sb.append(key);
				sb.append('.');
				sb.append(alias);
				sb.append(' ');
			}
			ps.setValue(STRUCTUREVIEWER_ALIASES_PREFERENCE_NAME, sb.toString());
			
			if (fPropertyChangeListener != null) {
				ps.removePropertyChangeListener(fPropertyChangeListener);
				fPropertyChangeListener= null;
			}
		}
		
		super.shutdown();
		
		if (fgDisposeOnShutdownImages != null) {
			Iterator i= fgDisposeOnShutdownImages.iterator();
			while (i.hasNext()) {
				Image img= (Image) i.next();
				if (!img.isDisposed())
					img.dispose();
			}
			fgImages= null;
		}
	}
	
	/**
	 * Performs the comparison described by the given input and opens a
	 * compare editor on the result.
	 *
	 * @param input the input on which to open the compare editor
	 * @param page the workbench page on which to create a new compare editor
	 * @param editor if not null the input is opened in this editor
	 * @see CompareEditorInput
	 */
	public void openCompareEditor(CompareEditorInput input, IWorkbenchPage page, IReusableEditor editor) {
	    
		if (compareResultOK(input)) {
			
			if (editor != null) {	// reuse the given editor
				editor.setInput(input);
				return;
			}
			
			if (page == null)
				page= getActivePage();
			if (page != null) {
				// open new CompareEditor on page
				try {
					page.openEditor(input, COMPARE_EDITOR);
				} catch (PartInitException e) {
					MessageDialog.openError(getShell(), Utilities.getString("CompareUIPlugin.openEditorError"), e.getMessage()); //$NON-NLS-1$
				}		
			} else {
				MessageDialog.openError(getShell(),
						Utilities.getString("CompareUIPlugin.openEditorError"), //$NON-NLS-1$
						Utilities.getString("CompareUIPlugin.noActiveWorkbenchPage")); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Performs the comparison described by the given input and opens a
	 * compare dialog on the result.
	 *
	 * @param input the input on which to open the compare editor
	 * @see CompareEditorInput
	 */
	public void openCompareDialog(final CompareEditorInput input) {
				
		if (compareResultOK(input)) {
			CompareDialog dialog= new CompareDialog(getShell(), input);
			dialog.open();
		}
	}
	
	/**
	 * @return <code>true</code> if compare result is OK to show, <code>false</code> otherwise
	 */
	private boolean compareResultOK(CompareEditorInput input) {
		final Shell shell= getShell();
		try {
			
			// run operation in separate thread and make it canceable
			PlatformUI.getWorkbench().getProgressService().run(true, true, input);
			
			String message= input.getMessage();
			if (message != null) {
				MessageDialog.openError(shell, Utilities.getString("CompareUIPlugin.compareFailed"), message); //$NON-NLS-1$
				return false;
			}
			
			if (input.getCompareResult() == null) {
				MessageDialog.openInformation(shell, Utilities.getString("CompareUIPlugin.dialogTitle"), Utilities.getString("CompareUIPlugin.noDifferences")); //$NON-NLS-2$ //$NON-NLS-1$
				return false;
			}
			
			return true;

		} catch (InterruptedException x) {
			// cancelled by user		
		} catch (InvocationTargetException x) {
			MessageDialog.openError(shell, Utilities.getString("CompareUIPlugin.compareFailed"), x.getTargetException().getMessage()); //$NON-NLS-1$
		}
		return false;
	}
		
	/**
	 * Registers an image for the given type.
	 */
	private static void registerImage(String type, Image image, boolean dispose) {
		fgImages.put(normalizeCase(type), image);
		if (image != null && dispose) {
			fgDisposeOnShutdownImages.add(image);
		}
	}
	
	/**
	 * Registers an image descriptor for the given type.
	 *
	 * @param type the type
	 * @param descriptor the image descriptor
	 */
	public static void registerImageDescriptor(String type, ImageDescriptor descriptor) {
		fgImageDescriptors.put(normalizeCase(type), descriptor);
	}
	
	public static ImageDescriptor getImageDescriptor(String relativePath) {
		
		URL installURL= null;
		if (fgComparePlugin != null)
			installURL= fgComparePlugin.getDescriptor().getInstallURL();
					
		if (installURL != null) {
			try {
				URL url= new URL(installURL, Utilities.getIconPath(null) + relativePath);
				return ImageDescriptor.createFromURL(url);
			} catch (MalformedURLException e) {
				Assert.isTrue(false);
			}
		}
		return null;
	}
	
	/**
	 * Returns a shared image for the given type, or a generic image if none
	 * has been registered for the given type.
	 * <p>
	 * Note: Images returned from this method will be automitically disposed
	 * of when this plug-in shuts down. Callers must not dispose of these
	 * images themselves.
	 * </p>
	 *
	 * @param type the type
	 * @return the image
	 */
	public static Image getImage(String type) {
		
		type= normalizeCase(type);
		
		boolean dispose= false;
		Image image= null;
		if (type != null)
			image= (Image) fgImages.get(type);
		if (image == null) {
			ImageDescriptor id= (ImageDescriptor) fgImageDescriptors.get(type);
			if (id != null) {
				image= id.createImage();
				dispose= true;
			}
				
			if (image == null) {
				if (fgComparePlugin != null) {
					if (ITypedElement.FOLDER_TYPE.equals(type)) {
						image= getDefault().getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FOLDER);
						//image= SharedImages.getImage(ISharedImages.IMG_OBJ_FOLDER);
					} else {
						image= createWorkbenchImage(type);
						dispose= true;
					}
				} else {
					id= (ImageDescriptor) fgImageDescriptors.get(normalizeCase("file")); //$NON-NLS-1$
					image= id.createImage();
					dispose= true;
				}
			}
			if (image != null)
				registerImage(type, image, dispose);
		}
		return image;
	}
	
	/**
	 * Returns a shared image for the given adaptable.
	 * This convenience method queries the given adaptable
	 * for its <code>IWorkbenchAdapter.getImageDescriptor</code>, which it
	 * uses to create an image if it does not already have one.
	 * <p>
	 * Note: Images returned from this method will be automitically disposed
	 * of when this plug-in shuts down. Callers must not dispose of these
	 * images themselves.
	 * </p>
	 *
	 * @param adaptable the adaptable for which to find an image
	 * @return an image
	 */
	public static Image getImage(IAdaptable adaptable) {
		if (adaptable != null) {
			Object o= adaptable.getAdapter(IWorkbenchAdapter.class);
			if (o instanceof IWorkbenchAdapter) {
				ImageDescriptor id= ((IWorkbenchAdapter) o).getImageDescriptor(adaptable);
				if (id != null) {
					Image image= (Image)fgImages2.get(id);
					if (image == null) {
						image= id.createImage();
						try {
							fgImages2.put(id, image);
						} catch (NullPointerException ex) {
							// NeedWork
						}
						fgDisposeOnShutdownImages.add(image);

					}
					return image;
				}
			}
		}
		return null;
	}
	
	private static Image createWorkbenchImage(String type) {
		IEditorRegistry er= getDefault().getWorkbench().getEditorRegistry();
		ImageDescriptor id= er.getImageDescriptor("foo." + type); //$NON-NLS-1$
		return id.createImage();
	}
	
	/**
	 * Returns an structure creator descriptor for the given type.
	 *
	 * @param type the type for which to find a descriptor
	 * @return a descriptor for the given type, or <code>null</code> if no
	 *   descriptor has been registered
	 */
	public StructureCreatorDescriptor getStructureCreator(String type) {
		return (StructureCreatorDescriptor) fStructureCreators.search(type);
	}
	
	/**
	 * Returns a stream merger for the given type.
	 *
	 * @param type the type for which to find a stream merger
	 * @return a stream merger for the given type, or <code>null</code> if no
	 *   stream merger has been registered
	 */
	public IStreamMerger createStreamMerger(String type) {
		StreamMergerDescriptor descriptor= (StreamMergerDescriptor) fStreamMergers.search(type);
		if (descriptor != null)
			return descriptor.createStreamMerger();
		return null;
	}
	
	/**
	 * Returns a stream merger for the given content type.
	 *
	 * @param type the type for which to find a stream merger
	 * @return a stream merger for the given type, or <code>null</code> if no
	 *   stream merger has been registered
	 */
	public IStreamMerger createStreamMerger(IContentType type) {
		StreamMergerDescriptor descriptor= (StreamMergerDescriptor) fStreamMergers.search(type);
		if (descriptor != null)
			return descriptor.createStreamMerger();
		return null;
	}
	
	/**
	 * Returns a structure compare viewer based on an old viewer and an input object.
	 * If the old viewer is suitable for showing the input, the old viewer
	 * is returned. Otherwise, the input's type is used to find a viewer descriptor in the registry
	 * which in turn is used to create a structure compare viewer under the given parent composite.
	 * If no viewer descriptor can be found <code>null</code> is returned.
	 *
	 * @param oldViewer a new viewer is only created if this old viewer cannot show the given input
	 * @param input the input object for which to find a structure viewer
	 * @param parent the SWT parent composite under which the new viewer is created
	 * @param configuration a configuration which is passed to a newly created viewer
	 * @return the compare viewer which is suitable for the given input object or <code>null</code>
	 */
	public Viewer findStructureViewer(Viewer oldViewer, ICompareInput input, Composite parent,
				CompareConfiguration configuration) {

		if (input.getLeft() == null || input.getRight() == null)	// we don't show the structure of additions or deletions
			return null;
					
		// content type search
		IContentType ctype= getCommonType(getContentTypes(input));
		if (ctype != null) {
		    Viewer viewer= getViewer(fStructureMergeViewers.search(ctype), oldViewer, parent, configuration);
		    if (viewer != null)
		        return viewer;
		}
		
		// old style search
		String[] types= getTypes(input);
		String type= null;
		if (isHomogenous(types)) {
			type= normalizeCase(types[0]);
			IViewerDescriptor vd= (IViewerDescriptor) fStructureMergeViewers.search(type);
			if (vd == null) {
				String alias= (String) fStructureViewerAliases.get(type);
				if (alias != null)
					vd= (IViewerDescriptor) fStructureMergeViewers.search(alias);
			}
			if (vd != null)
				return vd.createViewer(oldViewer, parent, configuration);
		}
		
		// we didn't found any viewer so far.
		// now we try to find a structurecreator for the generic StructureDiffViewer
		
		StructureCreatorDescriptor scc= null;
		Object desc= fStructureCreators.search(ctype);	// search for content type
		if (desc instanceof StructureCreatorDescriptor)
		    scc= (StructureCreatorDescriptor) desc;
		if (scc == null && type != null)
		    scc= getStructureCreator(type);	// search for old-style type scheme
		if (scc != null) {
			IStructureCreator sc= scc.createStructureCreator();
			if (sc != null) {
				StructureDiffViewer sdv= new StructureDiffViewer(parent, configuration);
				sdv.setStructureCreator(sc);
				return sdv;
			}
		}
		return null;
	}
	
	/**
	 * Returns a content compare viewer based on an old viewer and an input object.
	 * If the old viewer is suitable for showing the input the old viewer
	 * is returned. Otherwise the input's type is used to find a viewer descriptor in the registry
	 * which in turn is used to create a content compare viewer under the given parent composite.
	 * If no viewer descriptor can be found <code>null</code> is returned.
	 *
	 * @param oldViewer a new viewer is only created if this old viewer cannot show the given input
	 * @param input the input object for which to find a content viewer
	 * @param parent the SWT parent composite under which the new viewer is created
	 * @param configuration a configuration which is passed to a newly created viewer
	 * @return the compare viewer which is suitable for the given input object or <code>null</code>
	 */
	public Viewer findContentViewer(Viewer oldViewer, Object in, Composite parent, CompareConfiguration cc) {
		
		if (in instanceof IStreamContentAccessor) {
			String type= ITypedElement.TEXT_TYPE;
			
			if (in instanceof ITypedElement) {
				ITypedElement tin= (ITypedElement) in;
			    		    
			    IContentType ct= getContentType(tin);
				if (ct != null) {
					Viewer viewer= getViewer(fContentViewers.search(ct), oldViewer, parent, cc);
					if (viewer != null)
						return viewer;
				}
			    
				String ty= tin.getType();
				if (ty != null)
					type= ty;
			}
			
			Viewer viewer= getViewer(fContentViewers.search(type), oldViewer, parent, cc);
			if (viewer != null)
				return viewer;
			// fallback
			return new SimpleTextViewer(parent);
		}

		if (!(in instanceof ICompareInput))
			return null;
			
		ICompareInput input= (ICompareInput) in;
		
		IContentType ctype= getCommonType(getContentTypes(input));
		if (ctype != null) {
			Viewer viewer= getViewer(fContentMergeViewers.search(ctype), oldViewer, parent, cc);
			if (viewer != null)
				return viewer;
		}
		
		String[] types= getTypes(input);
		String type= null;
		if (isHomogenous(types))
			type= types[0];
		
		if (ITypedElement.FOLDER_TYPE.equals(type))
			return null;
			
		if (type == null) {
			int n= 0;
			for (int i= 0; i < types.length; i++)
				if (!ITypedElement.UNKNOWN_TYPE.equals(types[i])) {
					n++;
					if (type == null)
						type= types[i];	// remember the first known type
				}
			if (n > 1)	// don't use the type if there were more than one
				type= null;
		}
		
		if (type != null) {
			Viewer viewer= getViewer(fContentMergeViewers.search(type), oldViewer, parent, cc);
			if (viewer != null)
				return viewer;
		}

		// fallback
		String leftType= guessType(input.getLeft());
		String rightType= guessType(input.getRight());
			
		if (leftType != null || rightType != null) {
			boolean right_text= rightType != null && ITypedElement.TEXT_TYPE.equals(rightType);
			boolean left_text= leftType != null && ITypedElement.TEXT_TYPE.equals(leftType);
			if ((leftType == null && right_text) || (left_text && rightType == null) || (left_text && right_text))
				type= ITypedElement.TEXT_TYPE;
			else
				type= BINARY_TYPE;
			
			IViewerDescriptor vd= (IViewerDescriptor) fContentMergeViewers.search(type);
			if (vd != null)
				return vd.createViewer(oldViewer, parent, cc);
		}
		return null;
	}
	
	private static Viewer getViewer(Object descriptor, Viewer oldViewer, Composite parent, CompareConfiguration cc) {    
	    if (descriptor instanceof IViewerDescriptor)
			return ((IViewerDescriptor)descriptor).createViewer(oldViewer, parent, cc);
	    return null;
	}
	
	private static String[] getTypes(ICompareInput input) {
		ITypedElement ancestor= input.getAncestor();
		ITypedElement left= input.getLeft();
		ITypedElement right= input.getRight();
		
		ArrayList tmp= new ArrayList();		
		if (ancestor != null) {
			String type= ancestor.getType();
			if (type != null)
			    tmp.add(normalizeCase(type));
		}
		if (left != null) {
			String type= left.getType();
			if (type != null)
			    tmp.add(normalizeCase(type));
		}
		if (right != null) {
			String type= right.getType();
			if (type != null)
			    tmp.add(normalizeCase(type));
		}
		return (String[]) tmp.toArray(new String[tmp.size()]);
	}
		
	private static IContentType[] getContentTypes(ICompareInput input) {
		ITypedElement ancestor= input.getAncestor();
		ITypedElement left= input.getLeft();
		ITypedElement right= input.getRight();
		
		ArrayList tmp= new ArrayList();				
	    IContentType type= getContentType(ancestor);
		if (type != null)
		    tmp.add(type);
	    type= getContentType(left);
		if (type != null)
		    tmp.add(type);
		type= getContentType(right);
		if (type != null)
		    tmp.add(type);
		
		return (IContentType[]) tmp.toArray(new IContentType[tmp.size()]);
	}
	
	private static IContentType getContentType(ITypedElement element) {
	    if (element == null)
	        return null;
	    String name= element.getName();
		IContentType[] associated= fgContentTypeManager.findContentTypesFor(name);
		if (associated.length > 0)
		    return associated[0];
        IContentType ct= null;
		if (element instanceof IStreamContentAccessor) {
		    IStreamContentAccessor isa= (IStreamContentAccessor) element;
            try {
                InputStream is= isa.getContents();
                if (is != null) {
	    		        InputStream bis= new BufferedInputStream(is);
	    		        try {
	    		            ct= fgContentTypeManager.findContentTypeFor(is, name);
                    } catch (IOException e) {
                    }
	    		        try {
	    		            	bis.close();
                    } catch (IOException e2) {
                        // silently ignored
                    }
    		    		}
            } catch (CoreException e1) {
            }
		}
        return ct;
	}
	
	/**
	 * Returns true if the given types are homogenous.
	 */
	private static boolean isHomogenous(String[] types) {
		switch (types.length) {
		case 1:
			return true;
		case 2:
			return types[0].equals(types[1]);
		case 3:
			return types[0].equals(types[1]) && types[1].equals(types[2]);
		}
		return false;
	}
	
	/**
	 * Returns the most specific content type that is common to the given inputs or null.
	 */
	private static IContentType getCommonType(IContentType[] types) {
	    Set s= null;
	    ArrayList l= null;
	    	switch (types.length) {
		case 1:
			return types[0];
		case 2:
		    l= new ArrayList();
		    s= toSet(l, types[0]);
		    s.retainAll(toSet(l, types[1]));
			break;
		case 3:
		    l= new ArrayList();
		    s= toSet(l, types[0]);
		    s.retainAll(toSet(l, types[1]));
		    s.retainAll(toSet(l, types[2]));
			break;
		}
		if (s != null && !s.isEmpty()) {
		    Iterator iter= l.iterator();
		    while (iter.hasNext()) {
		        IContentType ct= (IContentType) iter.next();
		        if (s.contains(ct))
		            return ct;
		    }
		}
		return null;
	}
	
	private static Set toSet(ArrayList l, IContentType ct) {
	    Set set= new HashSet();
	    for (; ct != null; ct= ct.getBaseType()) {
	        l.add(ct);
	        set.add(ct);
	    }
	    return set;
	}
	
	/**
	 * Guesses the file type of the given input.
	 * Returns ITypedElement.TEXT_TYPE if none of the first 10 lines is longer than 1000 bytes.
	 * Returns ITypedElement.UNKNOWN_TYPE otherwise.
	 * Returns <code>null</code> if the input isn't an <code>IStreamContentAccessor</code>.
	 */
	private static String guessType(ITypedElement input) {
		if (input instanceof IStreamContentAccessor) {
			IStreamContentAccessor sca= (IStreamContentAccessor) input;
			InputStream is= null;
			try {
				is= sca.getContents();
				if (is == null)
					return null;
				int lineLength= 0;
				int lines= 0;
				while (lines < 10) {
					int c= is.read();
					if (c == -1)	// EOF
						break;
					if (c == '\n' || c == '\r') { // reset line length
						lineLength= 0;
						lines++;
					} else
						lineLength++;
					if (lineLength > 1000)
						return ITypedElement.UNKNOWN_TYPE;
				}
				return ITypedElement.TEXT_TYPE;
			} catch (CoreException ex) {
				// be silent and return UNKNOWN_TYPE
			} catch (IOException ex) {
				// be silent and return UNKNOWN_TYPE
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException ex) {
						// silently ignored
					}
				}
			}
			return ITypedElement.UNKNOWN_TYPE;
		}
		return null;
	}
	
	private static String normalizeCase(String s) {
		if (NORMALIZE_CASE && s != null)
			return s.toUpperCase();
		return s;
	}
	
	//---- alias mgmt
	
	private void initPreferenceStore() {
		//System.out.println("initPreferenceStore");
		final IPreferenceStore ps= getPreferenceStore();
		if (ps != null) {
			String aliases= ps.getString(STRUCTUREVIEWER_ALIASES_PREFERENCE_NAME);
			//System.out.println("  <" + aliases + ">");
			if (aliases != null && aliases.length() > 0) {
				StringTokenizer st= new StringTokenizer(aliases, " ");	//$NON-NLS-1$
				while (st.hasMoreTokens()) {
					String pair= st.nextToken();
					int pos= pair.indexOf('.');
					if (pos > 0) {
						String key= pair.substring(0, pos);
						String alias= pair.substring(pos+1);
						fStructureViewerAliases.put(key, alias);
						//System.out.println("<" + key + "><" + alias + ">");
					}
				}
			}
			fFilter= new CompareFilter();
			fFilter.setFilters(ps.getString(ComparePreferencePage.PATH_FILTER));
			fPropertyChangeListener= new IPropertyChangeListener() {
				public void propertyChange(PropertyChangeEvent event) {
					if (ComparePreferencePage.PATH_FILTER.equals(event.getProperty()))
						fFilter.setFilters(ps.getString(ComparePreferencePage.PATH_FILTER));
				}
			};
			ps.addPropertyChangeListener(fPropertyChangeListener);
		}
	}
	
	public void addStructureViewerAlias(String type, String alias) {
		fStructureViewerAliases.put(normalizeCase(alias), normalizeCase(type));
	}
	
	public void removeAllStructureViewerAliases(String type) {
		String t= normalizeCase(type);
		Set entrySet= fStructureViewerAliases.entrySet();
		for (Iterator iter= entrySet.iterator(); iter.hasNext(); ) {
			Map.Entry entry= (Map.Entry)iter.next();
			if (entry.getValue().equals(t))
				iter.remove();
		}
	}
	
	/**
	 * Returns an array of all editors that have an unsaved content. If the identical content is 
	 * presented in more than one editor, only one of those editor parts is part of the result.
	 * 
	 * @return an array of all dirty editor parts.
	 */
	public static IEditorPart[] getDirtyEditors() {
		Set inputs= new HashSet();
		List result= new ArrayList(0);
		IWorkbench workbench= getDefault().getWorkbench();
		IWorkbenchWindow[] windows= workbench.getWorkbenchWindows();
		for (int i= 0; i < windows.length; i++) {
			IWorkbenchPage[] pages= windows[i].getPages();
			for (int x= 0; x < pages.length; x++) {
				IEditorPart[] editors= pages[x].getDirtyEditors();
				for (int z= 0; z < editors.length; z++) {
					IEditorPart ep= editors[z];
					IEditorInput input= ep.getEditorInput();
					if (!inputs.contains(input)) {
						inputs.add(input);
						result.add(ep);
					}
				}
			}
		}
		return (IEditorPart[])result.toArray(new IEditorPart[result.size()]);
	}
		
	public boolean filter(String name, boolean isFolder, boolean isArchive) {
	    if (fFilter != null)
	        return fFilter.filter(name, isFolder, isArchive);
	    return false;
	}

	public static void logErrorMessage(String message) {
		if (message == null)
			message= ""; //$NON-NLS-1$
		log(new Status(IStatus.ERROR, getPluginId(), INTERNAL_ERROR, message, null));
	}

	public static void log(Throwable e) {
		log(new Status(IStatus.ERROR, getPluginId(), INTERNAL_ERROR, CompareMessages.getString("ComparePlugin.internal_error"), e)); //$NON-NLS-1$
	}
	
	public static void log(IStatus status) {
		getDefault().getLog().log(status);
	}
}
