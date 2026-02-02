import siteConfig from '@generated/docusaurus.config';
import type * as PrismNamespace from 'prismjs';

export default function prismIncludeLanguages(
  PrismObject: typeof PrismNamespace,
): void {
  const {
    themeConfig: {prism},
  } = siteConfig;
  const {additionalLanguages} = prism as {additionalLanguages: string[]};

  globalThis.Prism = PrismObject;

  // Load languages in dependency order
  // scala requires java, so load java first
  additionalLanguages.forEach((lang) => {
    if (lang === 'scala') {
      // scala depends on java
      require('prismjs/components/prism-java');
      require('prismjs/components/prism-scala');
    } else if (lang === 'docker') {
      require('prismjs/components/prism-docker');
    } else {
      try {
        require(`prismjs/components/prism-${lang}`);
      } catch {
        // Language not found, skip
      }
    }
  });

  // Register constellation-lang custom grammar
  PrismObject.languages['constellation'] = {
    comment: {
      pattern: /#.*/,
      greedy: true,
    },
    string: {
      pattern: /"(?:[^"\\]|\\.)*"/,
      greedy: true,
    },
    keyword: /\b(?:in|out|type|when|otherwise|branch|with)\b/,
    builtin:
      /\b(?:String|Int|Float|Boolean|List|Optional|Candidates|Record|Any|Nothing)\b/,
    boolean: /\b(?:true|false)\b/,
    number: /\b\d+(?:\.\d+)?\b/,
    function: /\b[A-Z][a-zA-Z0-9]*(?=\s*\()/,
    operator: /\+\+?|->|=>|>=|<=|!=|==|\?\?|[+\-*/><]/,
    punctuation: /[{}()\[\]:,=.]/,
  };

  delete (globalThis as any).Prism;
}
