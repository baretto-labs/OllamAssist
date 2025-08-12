package fr.baretto.ollamassist.component;

import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * WorkspaceFileSelector est un composant Swing autonome qui permet à l'utilisateur
 * de gérer une liste de fichiers.
 * <p>
 * Caractéristiques :
 * - Affiche les fichiers avec leur icône et leur nom, simulant un explorateur de fichiers.
 * - Permet d'ajouter un ou plusieurs fichiers via un sélecteur de fichiers natif.
 * - Permet de supprimer les fichiers sélectionnés dans la liste.
 * - Le composant est encapsulé dans un JPanel et peut être facilement intégré
 * dans n'importe quelle fenêtre Swing (comme un JFrame ou une Tool Window de plugin IntelliJ).
 */
public class WorkspaceFileSelector extends JPanel {

    private final DefaultListModel<File> fileListModel;
    @Getter
    private final JBList<File> fileList;
    private final @NotNull Project project;

    /**
     * Construit le panneau de sélection de fichiers.
     */
    public WorkspaceFileSelector(@NotNull Project project) {
        super(new BorderLayout(5, 5));
        setBorder(JBUI.Borders.empty(10));

        this.fileListModel = new DefaultListModel<>();
        this.fileList = new JBList<>(fileListModel);
        this.project = project;
        this.fileList.setCellRenderer(new FileCellRenderer());
        this.fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.fileList.setVisibleRowCount(15);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);


        JButton addButton = new JButton("Ajouter...");
        addButton.setIcon(UIManager.getIcon("FileChooser.newFolderIcon"));
        addButton.addActionListener(this::addFilesAction);

        JButton removeButton = new JButton("Supprimer");
        removeButton.setIcon(UIManager.getIcon("Tree.closedIcon"));
        removeButton.addActionListener(this::removeFilesAction);

        toolBar.add(addButton);
        toolBar.add(removeButton);

        removeButton.setEnabled(false);
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeButton.setEnabled(fileList.getSelectedIndex() != -1);
            }
        });


        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(fileList), BorderLayout.CENTER);

        FileEditorManagerListener listener = new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                VirtualFile newFile = event.getNewFile();
                if (newFile != null) {
                    File physicalFile = VfsUtilCore.virtualToIoFile(newFile);
                    if (!fileListModel.contains(physicalFile)) {
                        fileListModel.addElement(physicalFile);
                    }
                    fileList.setSelectedValue(physicalFile, true);
                }
            }
        };

        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);


    }

    public void addFilesAction(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            File baseFile = VfsUtilCore.virtualToIoFile(baseDir);
            fileChooser.setCurrentDirectory(baseFile);
        }

        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle("Sélectionner des fichiers à ajouter");

        int option = fileChooser.showOpenDialog(this);

        if (option == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            for (File file : selectedFiles) {
                if (!fileListModel.contains(file)) {
                    fileListModel.addElement(file);
                }
            }
        }
    }

    public void removeFilesAction(ActionEvent e) {
        int[] selectedIndices = fileList.getSelectedIndices();

        Arrays.sort(selectedIndices);

        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            fileListModel.removeElementAt(selectedIndices[i]);
        }
    }

    /**
     * Renvoie la liste des fichiers actuellement présents dans le sélecteur.
     *
     * @return Une List<File> contenant les fichiers.
     */
    public List<File> getSelectedFiles() {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < fileListModel.getSize(); i++) {
            files.add(fileListModel.getElementAt(i));
        }
        return files;
    }

    /**
     * CellRenderer personnalisé pour afficher une icône de fichier et son nom.
     */
    private static class FileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (c instanceof JLabel label && value instanceof File file) {
                label.setText(file.getName());

                Icon icon = FileSystemView.getFileSystemView().getSystemIcon(file);
                label.setIcon(icon);

                label.setIconTextGap(5);
            }

            return c;
        }
    }
}