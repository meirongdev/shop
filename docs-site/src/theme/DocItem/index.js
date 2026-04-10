import React from 'react';
import clsx from 'clsx';
import {
  HtmlClassNameProvider,
  ThemeClassNames,
} from '@docusaurus/theme-common';
import {DocProvider} from '@docusaurus/plugin-content-docs/client';
import Layout from '@theme/Layout';
import MDXContent from '@theme/MDXContent';
import TOC from '@theme/TOC';
import DocItemMetadata from '@theme/DocItem/Metadata';
import DocItemLayout from '@theme/DocItem/Layout';

function PlainMdxDocFallback({content}) {
  const MDXComponent = content;
  const frontMatter = content?.frontMatter ?? {};
  const title = frontMatter.title ?? content?.contentTitle ?? 'Document';
  const hideTOC = frontMatter.hide_table_of_contents;

  return (
    <HtmlClassNameProvider
      className={clsx(
        frontMatter.wrapperClassName ?? ThemeClassNames.wrapper.mdxPages,
        ThemeClassNames.page.mdxPage,
      )}>
      <Layout title={title}>
        <main className="container container--fluid margin-vert--lg">
          <div className="row">
            <div className={clsx('col', !hideTOC && 'col--8')}>
              <article>
                <MDXContent>
                  <MDXComponent />
                </MDXContent>
              </article>
            </div>
            {!hideTOC && content?.toc?.length > 0 && (
              <div className="col col--2">
                <TOC
                  toc={content.toc}
                  minHeadingLevel={frontMatter.toc_min_heading_level}
                  maxHeadingLevel={frontMatter.toc_max_heading_level}
                />
              </div>
            )}
          </div>
        </main>
      </Layout>
    </HtmlClassNameProvider>
  );
}

export default function DocItem(props) {
  const content = props.content;

  if (!content?.metadata) {
    return <PlainMdxDocFallback content={content} />;
  }

  const docHtmlClassName = `docs-doc-id-${content.metadata.id}`;
  const MDXComponent = content;

  return (
    <DocProvider content={content}>
      <HtmlClassNameProvider className={docHtmlClassName}>
        <DocItemMetadata />
        <DocItemLayout>
          <MDXComponent />
        </DocItemLayout>
      </HtmlClassNameProvider>
    </DocProvider>
  );
}
