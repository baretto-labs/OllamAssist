package fr.baretto.ollamassist.component;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.chat.rag.WorkspaceContextRetriever;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class WorkspaceFileSelector extends JPanel implements WorkspaceFileSelectorListener {

    private final DefaultTableModel tableModel;
    @Getter
    private final JBTable fileTable;
    private final @NotNull Project project;
    private final WorkspaceContextRetriever workspaceContextRetriever;

    public WorkspaceFileSelector(@NotNull Project project) {
        super(new BorderLayout(5, 5));

        workspaceContextRetriever = project.getService(WorkspaceContextRetriever.class);
        workspaceContextRetriever.subscribe(this);
        setBorder(JBUI.Borders.empty(10));

        String[] columnNames = {"File", "Tokens", "Modification Date"};
        this.tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) return Integer.class; // Tokens
                if (columnIndex == 2) return Date.class;   // Date
                return Object.class;                       // File
            }
        };

        this.fileTable = new JBTable(tableModel);
        this.project = project;

        fileTable.setAutoCreateRowSorter(true);
        fileTable.setFillsViewportHeight(true);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        fileTable.getColumnModel().getColumn(0).setCellRenderer(new FileIconRenderer());

        fileTable.setDefaultRenderer(Integer.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (c instanceof JLabel label && value instanceof Integer tokenCount) {
                    label.setText(String.format("%,d tokens", tokenCount));
                    label.setHorizontalAlignment(SwingConstants.RIGHT);
                }

                return c;
            }
        });

        fileTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Largeur réduite pour les tokens
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(150);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        JButton addButton = createToolbarButton(IconUtils.ADD_TO_CONTEXT, "Add files", this::addFilesAction);
        JButton removeButton = createToolbarButton(IconUtils.REMOVE_TO_CONTEXT, "Remove files", this::removeFilesAction);

        toolBar.add(addButton);
        toolBar.add(removeButton);

        removeButton.setEnabled(false);
        fileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeButton.setEnabled(fileTable.getSelectedRowCount() > 0);
            }
        });

        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(fileTable), BorderLayout.CENTER);

    }

    private JButton createToolbarButton(Icon icon, String tooltip, java.awt.event.ActionListener listener) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(24, 24));
        button.addActionListener(listener);
        return button;
    }

    private int estimateTokenCount(File file) {
        try {
            String content = Files.readString(file.toPath());
            return (int) Math.ceil(content.length() / 4.0);
        } catch (Exception e) {
            return 0;
        }
    }

    public void addFilesAction(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor();
        descriptor.setTitle("Add Files to OllamAssist Context");
        descriptor.setDescription("Select files to add to context");

        VirtualFile[] selectedVirtualFiles = FileChooser.chooseFiles(descriptor, project, null);

        for (VirtualFile vFile : selectedVirtualFiles) {
            File file = VfsUtilCore.virtualToIoFile(vFile);
            newFileAdded(file);
            workspaceContextRetriever.addFile(file);
        }
    }

    public void removeFilesAction(ActionEvent e) {
        int[] selectedRows = fileTable.getSelectedRows();
        List<File> filesToRemove = new ArrayList<>();


        for (int viewRow : selectedRows) {
            int modelRow = fileTable.convertRowIndexToModel(viewRow);
            File file = (File) tableModel.getValueAt(modelRow, 0);
            filesToRemove.add(file);
        }

        Arrays.sort(selectedRows);
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            int modelRow = fileTable.convertRowIndexToModel(selectedRows[i]);
            tableModel.removeRow(modelRow);
        }

        for (File file : filesToRemove) {
            workspaceContextRetriever.removeFile(file);
        }
    }

    public List<File> getSelectedFiles() {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            files.add((File) tableModel.getValueAt(i, 0));
        }
        return files;
    }

    public int getTotalTokens() {
        int total = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object tokenValue = tableModel.getValueAt(i, 1);
            if (tokenValue instanceof Integer tokenSize) {
                total += tokenSize;
            }
        }
        return total;
    }

    @Override
    public void newFileAdded(File file) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            File existingFile = (File) tableModel.getValueAt(i, 0);
            if (existingFile.getAbsolutePath().equals(file.getAbsolutePath())) {
                return;
            }
        }

        int tokenCount = estimateTokenCount(file);

        Object[] rowData = {
                file,
                tokenCount,
                new Date(file.lastModified())
        };
        tableModel.addRow(rowData);
    }


    /**
     * Renderer personnalisé pour afficher les fichiers avec leur icône spécifique
     */
    private static class FileIconRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {

            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column
            );

            if (value instanceof File file) {
                Icon fileIcon = getFileIcon(file);

                label.setText(file.getName());
                label.setIcon(fileIcon);
                label.setIconTextGap(5);
            }

            return label;
        }

        private Icon getFileIcon(File file) {
            try {
                String fileName = file.getName();
                FileTypeManager fileTypeManager = FileTypeManager.getInstance();
                FileType fileType = fileTypeManager.getFileTypeByFileName(fileName);

                return fileType.getIcon();
            } catch (Exception e) {
                return IconLoader.getIcon("/general/file.svg", WorkspaceFileSelector.class);
            }
        }
    }
}