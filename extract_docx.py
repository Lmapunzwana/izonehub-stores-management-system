import zipfile
import xml.etree.ElementTree as ET
import sys

def extract_text_from_docx(docx_path):
    with zipfile.ZipFile(docx_path) as docx:
        xml_content = docx.read('word/document.xml')
    tree = ET.fromstring(xml_content)
    text = []
    for node in tree.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p'):
        t = "".join(node.itertext())
        if t.strip():
            text.append(t.strip())
    return '\n'.join(text)

print(extract_text_from_docx(sys.argv[1]))
