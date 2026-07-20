import os
import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle
from reportlab.pdfgen import canvas

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super().showPage()
        super().save()

    def draw_page_decorations(self, page_count):
        self.saveState()
        
        # 1. Cover Page styling
        if self._pageNumber == 1:
            # Draw decorative sidebar gradient / block on cover page
            self.setFillColor(colors.HexColor("#1F7A3D")) # Forest Green
            self.rect(0, 0, 18, 792, fill=1, stroke=0)
            self.setFillColor(colors.HexColor("#0D4259")) # Slate Blue
            self.rect(18, 0, 10, 792, fill=1, stroke=0)
            self.restoreState()
            return

        # 2. Inner Pages styling
        self.setFont("Helvetica-Bold", 8)
        self.setFillColor(colors.HexColor("#0D4259"))
        
        # Running Header
        self.drawString(54, 755, "NEW SAHARA VENTURES — STORES MANAGEMENT SYSTEM GUIDE")
        self.setLineWidth(0.5)
        self.setStrokeColor(colors.HexColor("#CCCCCC"))
        self.line(54, 747, 558, 747)
        
        # Running Footer
        self.line(54, 50, 558, 50)
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#666666"))
        self.drawString(54, 38, "Confidential — Internal Operations Manual")
        page_text = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 38, page_text)
        
        self.restoreState()

def build_pdf(filename="docs/User_Guide_Managers.pdf"):
    # Ensure docs folder exists
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=72,
        bottomMargin=72
    )

    styles = getSampleStyleSheet()
    
    # Custom Brand Palette
    c_primary = colors.HexColor("#1F7A3D")   # Forest Green
    c_secondary = colors.HexColor("#0D4259") # Slate Blue
    c_dark = colors.HexColor("#222222")      # Dark Grey
    c_accent = colors.HexColor("#BCF0FF")    # Ice Blue
    
    # Custom Paragraph Styles
    title_style = ParagraphStyle(
        'CoverTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=32,
        leading=38,
        textColor=c_primary,
        spaceAfter=10
    )
    
    subtitle_style = ParagraphStyle(
        'CoverSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=16,
        leading=22,
        textColor=c_secondary,
        spaceAfter=30
    )
    
    metadata_style = ParagraphStyle(
        'CoverMeta',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=10,
        leading=14,
        textColor=colors.HexColor("#555555"),
        spaceAfter=4
    )

    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=24,
        textColor=c_secondary,
        spaceBefore=18,
        spaceAfter=10,
        keepWithNext=True
    )

    h2_style = ParagraphStyle(
        'Heading2_Custom',
        parent=styles['Heading2'],
        fontName='Helvetica-Bold',
        fontSize=13,
        leading=17,
        textColor=c_primary,
        spaceBefore=12,
        spaceAfter=6,
        keepWithNext=True
    )

    body_style = ParagraphStyle(
        'Body_Custom',
        parent=styles['BodyText'],
        fontName='Helvetica',
        fontSize=10,
        leading=14,
        textColor=c_dark,
        spaceAfter=8
    )

    bullet_style = ParagraphStyle(
        'Bullet_Custom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=10,
        leading=14,
        textColor=c_dark,
        leftIndent=15,
        firstLineIndent=-10,
        spaceAfter=5
    )

    callout_style = ParagraphStyle(
        'Callout_Style',
        parent=styles['Normal'],
        fontName='Helvetica-Oblique',
        fontSize=9.5,
        leading=13,
        textColor=c_secondary
    )

    story = []

    # ──────────────────────────────────────────────────────────────────────────
    # COVER PAGE
    # ──────────────────────────────────────────────────────────────────────────
    story.append(Spacer(1, 100))
    story.append(Paragraph("STORES MANAGEMENT<br/>SYSTEM", title_style))
    story.append(Paragraph("Standard Operating Guide for Managers", subtitle_style))
    
    # Decorative horizontal line
    story.append(Table(
        [['']], 
        colWidths=[504], 
        rowHeights=[4], 
        style=TableStyle([
            ('BACKGROUND', (0,0), (-1,-1), c_primary),
            ('BOTTOMPADDING', (0,0), (-1,-1), 0),
            ('TOPPADDING', (0,0), (-1,-1), 0),
        ])
    ))
    story.append(Spacer(1, 180))

    # Metadata block
    story.append(Paragraph("DOCUMENT STATUS: RELEASED", metadata_style))
    story.append(Paragraph("ORGANIZATION: NEW SAHARA VENTURES (PVT) LTD", metadata_style))
    story.append(Paragraph("TARGET ROLES: CENTRAL STORE MANAGERS & SITE STORE MANAGERS", metadata_style))
    story.append(Paragraph("DATE: JULY 2026", metadata_style))
    story.append(Paragraph("VERSION: 1.2 (STABLE)", metadata_style))
    story.append(PageBreak())

    # ──────────────────────────────────────────────────────────────────────────
    # CHAPTER 1: INTRODUCTION & OVERVIEW
    # ──────────────────────────────────────────────────────────────────────────
    story.append(Paragraph("Chapter 1: Overview & Architecture", h1_style))
    story.append(Paragraph(
        "Welcome to the New Sahara Ventures Stores Management System. This platform bridges "
        "communication and tracks inventory flow between the Central Head Office Warehouse (Athlone) "
        "and various construction site stores (SABI, SUNPORTS, Murombedzi). It ensures real-time "
        "visibility, strict financial compliance, and total accountability.",
        body_style
    ))
    story.append(Spacer(1, 8))

    # Roles Table
    roles_data = [
        [Paragraph("<b>Role</b>", body_style), Paragraph("<b>Primary Location</b>", body_style), Paragraph("<b>Key Responsibilities</b>", body_style)],
        [
            Paragraph("<b>Central Store Manager</b>", body_style),
            Paragraph("Athlone & Head Office", body_style),
            Paragraph("Supplier order tracking (GRN), review/approve material requests, process dispatches, inspect site returns, resolve stock discrepancies.", body_style)
        ],
        [
            Paragraph("<b>Site Store Manager</b>", body_style),
            Paragraph("Active Project Sites (SABI, SUNPORTS, etc.)", body_style),
            Paragraph("Create material requests, log daily usage (consumption), perform shelf stock counts, initiate returns for surplus materials.", body_style)
        ]
    ]
    t_roles = Table(roles_data, colWidths=[130, 110, 264])
    t_roles.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_accent),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#DDDDDD")),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 6),
        ('BOTTOMPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(t_roles)
    story.append(Spacer(1, 15))

    # ──────────────────────────────────────────────────────────────────────────
    # CHAPTER 2: CENTRAL STORE MANAGER MANUAL
    # ──────────────────────────────────────────────────────────────────────────
    story.append(Paragraph("Chapter 2: Central Store Manager Operating Procedures", h1_style))
    
    story.append(Paragraph("2.1 Catalog & Inventory Management", h2_style))
    story.append(Paragraph(
        "The <b>Items</b> page lists all standard materials (e.g. Portland Cement, Steel Rebar, PPE). "
        "As a Central Manager, you have permission to register new items. Crucially, the system tracks "
        "four stages of stock quantity:",
        body_style
    ))
    story.append(Paragraph("• <b>Available:</b> Physical stock on hand in the central warehouse, ready for allocation.", bullet_style))
    story.append(Paragraph("• <b>Reserved:</b> Allocated to approved site requests but not yet physically loaded/dispatched.", bullet_style))
    story.append(Paragraph("• <b>Incoming:</b> Promised supplier quantities currently in transit or on expected receipts.", bullet_style))
    story.append(Paragraph("• <b>Frozen:</b> Quantities locked under active discrepancy investigations (e.g. damaged deliveries).", bullet_style))
    
    story.append(Paragraph("2.2 Expected Receipts & Goods Received Notes (GRN)", h2_style))
    story.append(Paragraph(
        "When purchasing materials from a supplier (e.g. Jinko, Chint, Lyfra):",
        body_style
    ))
    story.append(Paragraph("1. Navigate to <b>Expected Receipts</b> and click <b>Add Expected Receipt</b>. Enter the supplier, items, expected quantities, and date.", bullet_style))
    story.append(Paragraph("2. Upon the delivery truck's arrival, open the receipt, verify the package, and click <b>Confirm GRN</b>.", bullet_style))
    story.append(Paragraph("3. Record the physical quantities received. If there is a deficit or items are broken, note the damaged amount. The system automatically creates an active <b>Discrepancy</b> record for the variance.", bullet_style))

    story.append(Paragraph("2.3 Material Request Approval & Dispatch", h2_style))
    story.append(Paragraph(
        "Site managers request stock using the system. You are the approval authority:",
        body_style
    ))
    story.append(Paragraph("• <b>Approvals:</b> Go to <b>Material Requests</b>. Review the request. You can reduce approved quantities based on central stock levels, or reject the request with a note.", bullet_style))
    story.append(Paragraph("• <b>Dispatches:</b> Approved requests move to the <b>Dispatch</b> queue. When a truck driver or runner arrives to collect, click <b>Dispatch</b>. You <b>must</b> record the Collector's Name and Employee ID. This moves the inventory to <i>In Transit</i> and deducts it from the Central Store.", bullet_style))

    story.append(Paragraph("2.4 Returns & Discrepancies", h2_style))
    story.append(Paragraph(
        "• <b>Confirming Returns:</b> When sites return unused material, they appear on the <b>Returns</b> page. Inspect the returned items, click <b>Confirm</b>, and assign them as either <i>Serviceable</i> (adds back to warehouse inventory) or <i>Damaged</i> (logs as write-off).",
        body_style
    ))
    story.append(Paragraph(
        "• <b>Resolving Discrepancies:</b> Any delivery or return mismatch goes to the <b>Discrepancies</b> board. Investigate the variance, then resolve it as either <i>Recovered</i> (supplier replaced, or missing item was found) or <i>Written Off</i> (permanently lost).",
        body_style
    ))
    
    # Callout box
    callout_data = [[
        Paragraph("<b>CRITICAL NOTE:</b> All transactions in the system are logged immediately in the immutable <i>Audit Log</i>. "
                  "Never share credentials, as every approval, dispatch, or write-off is tied to your account.", callout_style)
    ]]
    t_callout = Table(callout_data, colWidths=[504])
    t_callout.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#F0F9FF")),
        ('GRID', (0,0), (-1,-1), 1, c_accent),
        ('PADDING', (0,0), (-1,-1), 10),
    ]))
    story.append(Spacer(1, 10))
    story.append(t_callout)
    story.append(PageBreak())

    # ──────────────────────────────────────────────────────────────────────────
    # CHAPTER 3: SITE STORE MANAGER MANUAL
    # ──────────────────────────────────────────────────────────────────────────
    story.append(Paragraph("Chapter 3: Site Store Manager Operating Procedures", h1_style))
    
    story.append(Paragraph("3.1 Ordering Materials (Material Requests)", h2_style))
    story.append(Paragraph(
        "Site managers must maintain sufficient site inventory to avoid project delays:",
        body_style
    ))
    story.append(Paragraph("1. Navigate to <b>Material Requests</b> and click <b>New Request</b>.", bullet_style))
    story.append(Paragraph("2. Select your assigned <b>Project</b>, the source <b>Central Store</b> (Athlone), and add the requested items and quantities.", bullet_style))
    story.append(Paragraph("3. Click <b>Submit</b>. The Central Manager will review and approve. Once they dispatch, the items become <i>In Transit</i>.", bullet_style))
    story.append(Paragraph("4. Once the items physically arrive at the site, open the request and click <b>Confirm Receipt</b>. This officially moves the items into your site inventory.", bullet_style))

    story.append(Paragraph("3.2 Daily Usage Tracking (Consumption)", h2_style))
    story.append(Paragraph(
        "Every time cement, timber, or tools are used on site, they must be recorded in the system:",
        body_style
    ))
    story.append(Paragraph("• Go to the <b>Consumption</b> page.", bullet_style))
    story.append(Paragraph("• Find the item in your site inventory, click <b>Consume</b>, and enter the quantity used.", bullet_style))
    story.append(Paragraph("• Fill out the date and details (e.g. 'Used for Site 1 fence posts'). This deducts the quantities immediately from your site store inventory.", bullet_style))

    story.append(Paragraph("3.3 Inventory Auditing (Stock Counts)", h2_style))
    story.append(Paragraph(
        "To prevent loss and ensure accuracy, you should perform periodic audits:",
        body_style
    ))
    story.append(Paragraph("1. Go to <b>Stock Counts</b> and click <b>Initiate Count</b>. This freezes the database snapshot of quantities.", bullet_style))
    story.append(Paragraph("2. Physically count all items on your site's shelves and enter the physical numbers in the count sheet.", bullet_style))
    story.append(Paragraph("3. Click <b>Submit</b>. Any mismatches will calculate as a variance. If a variance exists, click <b>Raise Adjustment</b> to request approval to update the system.", bullet_style))

    story.append(Paragraph("3.4 Surplus Material Returns", h2_style))
    story.append(Paragraph(
        "At the end of a project, or if you have excess materials, you must return them to the warehouse:",
        body_style
    ))
    story.append(Paragraph("• Click <b>New Return</b>, select your site store, choose the warehouse, and list the items and quantities.", bullet_style))
    story.append(Paragraph("• Click <b>Initiate Return</b> and load the materials onto the transport truck.", bullet_style))
    story.append(Paragraph("• The items will be shown as <i>Awaiting Confirmation</i> until the Central Manager physically receives and inspects them in Athlone.", bullet_style))

    # ──────────────────────────────────────────────────────────────────────────
    # CHAPTER 4: OPERATIONAL BEST PRACTICES
    # ──────────────────────────────────────────────────────────────────────────
    story.append(Spacer(1, 15))
    story.append(Paragraph("Chapter 4: Operational Best Practices", h1_style))
    story.append(Paragraph(
        "To ensure the system works smoothly and inventory remains reconciled:",
        body_style
    ))
    story.append(Paragraph("• <b>Verify receipts immediately:</b> Do not delay confirming GRNs. If a truck arrives, count the items and confirm receipt on the spot.", bullet_style))
    story.append(Paragraph("• <b>Log consumption daily:</b> Waiting until the end of the week to log consumption leads to discrepancies, stock-outs, and inaccurate project budgets.", bullet_style))
    story.append(Paragraph("• <b>Double-check collector IDs:</b> During dispatch, always visually inspect the driver's license/employee badge and record it exactly. This ensures physical security.", bullet_style))
    story.append(Paragraph("• <b>Submit counts during slow hours:</b> Initiate stock counts early in the morning or after hours to ensure no consumption is logged while counting, which would skew the snapshot.", bullet_style))

    doc.build(story, canvasmaker=NumberedCanvas)
    print("PDF user guide successfully compiled at:", filename)

if __name__ == "__main__":
    build_pdf()
