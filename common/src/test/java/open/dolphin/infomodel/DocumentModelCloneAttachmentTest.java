package open.dolphin.infomodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DocumentModelCloneAttachmentTest {

    @Test
    public void cloneCopiesAttachmentAndRebindsDocument() throws Exception {
        DocumentModel original = new DocumentModel();
        AttachmentModel attachment = new AttachmentModel();
        attachment.setFileName("report.txt");
        attachment.setDocumentModel(original);
        original.addAttachment(attachment);

        DocumentModel cloned = (DocumentModel) original.clone();

        assertNotNull(cloned.getAttachment());
        assertEquals(1, cloned.getAttachment().size());
        AttachmentModel clonedAttachment = cloned.getAttachment().get(0);
        assertNotSame(attachment, clonedAttachment);
        assertSame(cloned, clonedAttachment.getDocumentModel());
    }
}
