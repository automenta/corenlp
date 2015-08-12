package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

public class AceMentionArgument {

  final protected String mRole;
  final protected AceEntityMention mContent;
  final private String mentionType; // in practice, event or relation

  public AceMentionArgument(String role,
      AceEntityMention content, String mentionType) {
    mRole = role;
    mContent = content;
    this.mentionType = mentionType;
  }

  public AceEntityMention getContent() { return mContent; }

  public String getRole() { return mRole; }

  public String toXml(int offset) {
    StringBuffer buffer = new StringBuffer();
    AceElement.appendOffset(buffer, offset);
    buffer.append('<').append(mentionType).append("_mention_argument REFID=\"").append(mContent.getId()).append("\" ROLE=\"").append(mRole).append("\">\n");
  
    
    //buffer.append(getContent().toXml(offset + 2));
    AceCharSeq ext = getContent().getExtent();
    buffer.append(ext.toXml("extent", offset + 2));
    buffer.append('\n');
  
    AceElement.appendOffset(buffer, offset);
    buffer.append("</").append(mentionType).append("_mention_argument>");
    return buffer.toString();
  }

  public String toXmlShort(int offset) {
    StringBuffer buffer = new StringBuffer();
    AceElement.appendOffset(buffer, offset);
    buffer.append('<').append(mentionType).append("_argument REFID=\"").append(mContent.getParent().getId()).append("\" ROLE=\"").append(mRole).append("\"/>");
    return buffer.toString();
  }

}